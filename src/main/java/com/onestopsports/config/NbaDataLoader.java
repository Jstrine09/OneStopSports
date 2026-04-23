package com.onestopsports.config;

import com.onestopsports.model.League;
import com.onestopsports.model.Player;
import com.onestopsports.model.Sport;
import com.onestopsports.model.Team;
import com.onestopsports.repository.LeagueRepository;
import com.onestopsports.repository.PlayerRepository;
import com.onestopsports.repository.SportRepository;
import com.onestopsports.repository.TeamRepository;
import com.onestopsports.service.NbaApiService;
import com.onestopsports.service.NbaApiService.NbaPlayer;
import com.onestopsports.service.NbaApiService.NbaTeam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// NbaDataLoader seeds the database with NBA teams and rosters on first startup.
// It runs independently from the football DataLoader — the two don't know about each other.
//
// Data source: balldontlie.io v1 API (free tier, Bearer token auth, 60 req/min limit)
// Seeding order: Sport → League → Teams → Players (with 1.1s sleep between team fetches)
//
// Idempotency strategy: mirrors the football DataLoader
//   - Skip entirely if all 30 teams are already in the DB
//   - If partially seeded (e.g. from a previous 429), skip only the teams that already exist
//   - Sport and League creation use find-or-create so re-runs don't create duplicates
@Component
public class NbaDataLoader implements CommandLineRunner { // CommandLineRunner = runs once at startup

    private static final Logger log = LoggerFactory.getLogger(NbaDataLoader.class);

    // There are exactly 30 NBA teams — we use this as our "fully seeded" marker
    private static final int NBA_TEAM_COUNT = 30;

    private final NbaApiService    nbaApiService;
    private final SportRepository  sportRepository;
    private final LeagueRepository leagueRepository;
    private final TeamRepository   teamRepository;
    private final PlayerRepository playerRepository;

    public NbaDataLoader(NbaApiService nbaApiService,
                         SportRepository sportRepository,
                         LeagueRepository leagueRepository,
                         TeamRepository teamRepository,
                         PlayerRepository playerRepository) {
        this.nbaApiService    = nbaApiService;
        this.sportRepository  = sportRepository;
        this.leagueRepository = leagueRepository;
        this.teamRepository   = teamRepository;
        this.playerRepository = playerRepository;
    }

    @Override
    public void run(String... args) {
        // Only consider fully seeded if all 30 teams exist.
        // This handles the case where a previous run hit a 429 mid-way:
        // sport + league might exist, but teams would be missing → we still re-run.
        boolean fullySeeded = sportRepository.findBySlug("basketball")
                .flatMap(s -> leagueRepository.findBySportId(s.getId())
                        .stream()
                        .filter(l -> "NBA".equals(l.getName()))
                        .findFirst())
                .map(l -> teamRepository.findByLeagueId(l.getId()).size() >= NBA_TEAM_COUNT)
                .orElse(false);

        if (fullySeeded) {
            log.info("[NbaDataLoader] All {} NBA teams already seeded — skipping.", NBA_TEAM_COUNT);
            return;
        }

        log.info("[NbaDataLoader] Seeding NBA data from balldontlie.io...");
        try {
            seed();
        } catch (Exception e) {
            // If something goes wrong (API down, 429, network issue), log and move on.
            // The app still starts — re-run the app to continue from where it left off.
            log.error("[NbaDataLoader] Seeding failed — app will start but NBA data may be incomplete. " +
                    "Re-run to resume. Cause: {}", e.getMessage());
        }
    }

    private void seed() throws InterruptedException {

        // ── 1. Sport ──────────────────────────────────────────────────────────
        // Find-or-create the Basketball sport — idempotent, safe to re-run.
        Sport basketball = sportRepository.findBySlug("basketball")
                .orElseGet(() -> sportRepository.save(
                        Sport.builder()
                                .name("Basketball")
                                .slug("basketball")
                                .iconUrl("https://upload.wikimedia.org/wikipedia/commons/thumb/7/7a/Basketball.png/240px-Basketball.png")
                                .build()));
        log.info("[NbaDataLoader] Sport: {}", basketball.getName());

        // ── 2. League ─────────────────────────────────────────────────────────
        // Find the NBA league if it already exists (from a previous partial run),
        // or create it fresh. externalId is null — balldontlie has no competition ID.
        // Routing in MatchService uses the sport slug ("basketball") instead.
        League nba = leagueRepository.findBySportId(basketball.getId())
                .stream()
                .filter(l -> "NBA".equals(l.getName()))
                .findFirst()
                .orElseGet(() -> leagueRepository.save(
                        League.builder()
                                .sport(basketball)
                                .name("NBA")
                                .country("United States")
                                .season("2024-25")
                                .externalId(null) // No external competition ID for basketball
                                .build()));
        log.info("[NbaDataLoader] League: {}", nba.getName());

        // ── 3. Teams ──────────────────────────────────────────────────────────
        NbaApiService.NbaTeamsResponse teamsResponse = nbaApiService.fetchAllTeams();

        if (teamsResponse == null || teamsResponse.data() == null) {
            log.warn("[NbaDataLoader] No teams returned from API — aborting.");
            return;
        }

        List<NbaTeam> apiTeams = teamsResponse.data();
        log.info("[NbaDataLoader] Fetched {} teams from balldontlie", apiTeams.size());

        // Build a set of team names already in the DB — used to skip teams that were
        // already seeded in a previous partial run (same pattern as football DataLoader's
        // per-league skip via leagueRepository.findByExternalId)
        Set<String> existingTeamNames = teamRepository.findByLeagueId(nba.getId())
                .stream()
                .map(Team::getName)
                .collect(Collectors.toSet());

        for (NbaTeam apiTeam : apiTeams) {

            // Skip this team if it was already seeded in a previous run
            if (existingTeamNames.contains(apiTeam.fullName())) {
                log.info("[NbaDataLoader]   {} already seeded, skipping.", apiTeam.fullName());
                continue;
            }

            // Save the team — crestUrl and stadium are null (not in free tier)
            Team team = teamRepository.save(
                    Team.builder()
                            .league(nba)
                            .name(apiTeam.fullName())          // e.g. "Boston Celtics"
                            .shortName(apiTeam.abbreviation()) // e.g. "BOS"
                            .country(apiTeam.city())           // e.g. "Boston"
                            .crestUrl(null)                    // Not available in free tier
                            .stadium(null)                     // Not available in free tier
                            .build());
            log.info("[NbaDataLoader]   Saved team: {}", team.getName());

            // ── 4. Players ────────────────────────────────────────────────────
            // Sleep 7 seconds before each team's player fetch.
            // If the team has more than 100 players (possible with historical data),
            // NbaApiService.fetchPlayersByTeam() will also sleep 2s between each page.
            Thread.sleep(7_000);
            List<NbaPlayer> players = nbaApiService.fetchPlayersByTeam(apiTeam.id());

            for (NbaPlayer apiPlayer : players) {
                // Jersey number is a String in balldontlie — parse to Integer for our DB
                Integer jerseyNumber = null;
                if (apiPlayer.jerseyNumber() != null && !apiPlayer.jerseyNumber().isBlank()) {
                    try {
                        jerseyNumber = Integer.parseInt(apiPlayer.jerseyNumber());
                    } catch (NumberFormatException ex) {
                        // Some non-numeric jerseys exist (e.g. "00") — skip rather than crash
                        log.warn("[NbaDataLoader] Could not parse jersey '{}' for {}",
                                apiPlayer.jerseyNumber(), apiPlayer.firstName());
                    }
                }

                // Combine first and last name into a single display name
                String fullName = (apiPlayer.firstName() + " " + apiPlayer.lastName()).trim();

                playerRepository.save(
                        Player.builder()
                                .team(team)
                                .name(fullName)
                                // Map abbreviated position to full name:
                                // "G" → "Guard", "F" → "Forward", "C" → "Center", etc.
                                .position(mapPosition(apiPlayer.position()))
                                .nationality(apiPlayer.country()) // e.g. "USA"
                                .jerseyNumber(jerseyNumber)
                                .dateOfBirth(null) // Not available in balldontlie free tier
                                .build());
            }

            log.info("[NbaDataLoader]     Saved {} players for {}", players.size(), team.getName());
        }

        log.info("[NbaDataLoader] Done! NBA seeded with {} teams.", apiTeams.size());
    }

    // Maps balldontlie's abbreviated position codes to the full position names
    // stored in the database. These full names match the POSITION_ORDER array in
    // the frontend's TeamDetailPage, so players get grouped into labelled sections.
    private String mapPosition(String pos) {
        if (pos == null || pos.isBlank()) return null;
        return switch (pos) {
            case "G"   -> "Guard";
            case "F"   -> "Forward";
            case "C"   -> "Center";
            case "G-F" -> "Guard-Forward";
            case "F-C" -> "Forward-Center";
            default    -> pos; // Return as-is for any unexpected value
        };
    }
}
