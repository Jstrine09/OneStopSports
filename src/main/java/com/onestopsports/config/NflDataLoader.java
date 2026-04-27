package com.onestopsports.config;

import com.onestopsports.model.League;
import com.onestopsports.model.Player;
import com.onestopsports.model.Sport;
import com.onestopsports.model.Team;
import com.onestopsports.repository.LeagueRepository;
import com.onestopsports.repository.PlayerRepository;
import com.onestopsports.repository.SportRepository;
import com.onestopsports.repository.TeamRepository;
import com.onestopsports.service.NflApiService;
import com.onestopsports.service.NflApiService.EspnAthlete;
import com.onestopsports.service.NflApiService.EspnLeague;
import com.onestopsports.service.NflApiService.EspnTeam;
import com.onestopsports.service.NflApiService.EspnTeamsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// NflDataLoader seeds the database with NFL teams and rosters on first startup.
// Runs independently from the football and NBA loaders — they don't coordinate with each other.
//
// Data source: ESPN's unofficial public API (no API key required)
// Endpoint pattern: https://site.api.espn.com/apis/site/v2/sports/football/nfl/...
//
// Seeding order:
//   1. Sport ("American Football", slug "american-football")
//   2. League ("NFL")
//   3. 32 teams (with crest URLs from ESPN logo CDN)
//   4. Roster for each team (~53 active players each)
//
// Idempotency: same strategy as NbaDataLoader
//   - Skip entirely if all 32 teams already exist
//   - If partially seeded, skip teams that already exist and only seed the missing ones
@Component
public class NflDataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(NflDataLoader.class);

    // The NFL has exactly 32 teams — used as the "fully seeded" marker
    private static final int NFL_TEAM_COUNT = 32;

    private final NflApiService    nflApiService;
    private final SportRepository  sportRepository;
    private final LeagueRepository leagueRepository;
    private final TeamRepository   teamRepository;
    private final PlayerRepository playerRepository;

    public NflDataLoader(NflApiService nflApiService,
                         SportRepository sportRepository,
                         LeagueRepository leagueRepository,
                         TeamRepository teamRepository,
                         PlayerRepository playerRepository) {
        this.nflApiService    = nflApiService;
        this.sportRepository  = sportRepository;
        this.leagueRepository = leagueRepository;
        this.teamRepository   = teamRepository;
        this.playerRepository = playerRepository;
    }

    @Override
    public void run(String... args) {
        // Check whether all 32 teams are already in the DB.
        // We check teams specifically rather than just sport/league existence,
        // because a previous run might have been interrupted mid-way through teams.
        boolean fullySeeded = sportRepository.findBySlug("american-football")
                .flatMap(s -> leagueRepository.findBySportId(s.getId())
                        .stream()
                        .filter(l -> "NFL".equals(l.getName()))
                        .findFirst())
                .map(l -> teamRepository.findByLeagueId(l.getId()).size() >= NFL_TEAM_COUNT)
                .orElse(false);

        if (fullySeeded) {
            log.info("[NflDataLoader] All {} NFL teams already seeded — skipping.", NFL_TEAM_COUNT);
            return;
        }

        log.info("[NflDataLoader] Seeding NFL data from ESPN...");
        try {
            seed();
        } catch (Exception e) {
            // If seeding fails (network error, ESPN API change, etc.) the app still starts.
            // Run the app again and the loader will resume from where it left off.
            log.error("[NflDataLoader] Seeding failed — app will start but NFL data may be incomplete. " +
                    "Re-run to resume. Cause: {}", e.getMessage());
        }
    }

    private void seed() throws InterruptedException {

        // ── 1. Sport ──────────────────────────────────────────────────────────
        // Find-or-create "American Football" so re-runs don't create duplicate sports.
        // Using "american-football" as the slug keeps it distinct from "football" (soccer).
        Sport americanFootball = sportRepository.findBySlug("american-football")
                .orElseGet(() -> sportRepository.save(
                        Sport.builder()
                                .name("American Football")
                                .slug("american-football")
                                .iconUrl("https://upload.wikimedia.org/wikipedia/commons/thumb/0/05/American_football.png/240px-American_football.png")
                                .build()));
        log.info("[NflDataLoader] Sport: {}", americanFootball.getName());

        // ── 2. League ─────────────────────────────────────────────────────────
        // The NFL — no external competition ID (externalId=null) because the ESPN API
        // doesn't use competition IDs. Routing uses the sport slug "american-football" instead.
        League nfl = leagueRepository.findBySportId(americanFootball.getId())
                .stream()
                .filter(l -> "NFL".equals(l.getName()))
                .findFirst()
                .orElseGet(() -> leagueRepository.save(
                        League.builder()
                                .sport(americanFootball)
                                .name("NFL")
                                .country("United States")
                                .season("2025-26") // The 2025 season runs Sep 2025 – Feb 2026
                                .externalId(null)  // No competition ID — routing by sport slug
                                .build()));
        log.info("[NflDataLoader] League: {}", nfl.getName());

        // ── 3. Teams ──────────────────────────────────────────────────────────
        // ESPN's teams endpoint nests teams under: sports[0].leagues[0].teams[*].team
        EspnTeamsResponse teamsResponse = nflApiService.fetchAllTeams();

        if (teamsResponse == null || teamsResponse.sports() == null || teamsResponse.sports().isEmpty()) {
            log.warn("[NflDataLoader] No teams returned from ESPN — aborting.");
            return;
        }

        // Navigate down the nested ESPN response to get the flat team list
        List<EspnLeague> leagues = teamsResponse.sports().get(0).leagues();
        if (leagues == null || leagues.isEmpty()) {
            log.warn("[NflDataLoader] No leagues in ESPN response — aborting.");
            return;
        }

        // Extract the list of EspnTeam objects from the wrapper entries
        List<EspnTeam> apiTeams = leagues.get(0).teams()
                .stream()
                .map(entry -> entry.team()) // Each entry is { "team": {...} } — unwrap
                .filter(team -> team != null)
                .toList();

        log.info("[NflDataLoader] Fetched {} teams from ESPN", apiTeams.size());

        // Record which team names already exist in the DB so we can skip them
        Set<String> existingTeamNames = teamRepository.findByLeagueId(nfl.getId())
                .stream()
                .map(Team::getName)
                .collect(Collectors.toSet());

        for (EspnTeam apiTeam : apiTeams) {

            // Skip teams that were already successfully seeded in a previous run
            if (existingTeamNames.contains(apiTeam.displayName())) {
                log.info("[NflDataLoader]   {} already seeded, skipping.", apiTeam.displayName());
                continue;
            }

            // Get the first logo URL (default light-mode version) for the crest.
            // Unlike basketball (no free-tier crest), ESPN provides logos for all NFL teams.
            String crestUrl = (apiTeam.logos() != null && !apiTeam.logos().isEmpty())
                    ? apiTeam.logos().get(0).href()
                    : null;

            Team team = teamRepository.save(
                    Team.builder()
                            .league(nfl)
                            .name(apiTeam.displayName())  // e.g. "Arizona Cardinals"
                            .shortName(apiTeam.abbreviation()) // e.g. "ARI"
                            .country(apiTeam.location())  // e.g. "Arizona" — city/state
                            .crestUrl(crestUrl)           // ESPN CDN URL — available on free tier
                            .stadium(null)                // Not available in this API response
                            .build());
            log.info("[NflDataLoader]   Saved team: {}", team.getName());

            // ── 4. Players ────────────────────────────────────────────────────
            // Sleep 1.5 seconds between roster fetches as a courtesy to ESPN's servers.
            // The ESPN API has no documented rate limit, but bursting 32 requests at once
            // could trigger throttling. 1.5s × 32 teams ≈ 48 seconds total seeding time.
            Thread.sleep(1_500);

            // fetchPlayersByTeam flattens the offense/defense/specialTeam groups for us
            List<EspnAthlete> players = nflApiService.fetchPlayersByTeam(apiTeam.id());

            for (EspnAthlete athlete : players) {
                // Jersey number is a String in the ESPN response — parse it to Integer for the DB
                Integer jerseyNumber = null;
                if (athlete.jersey() != null && !athlete.jersey().isBlank()) {
                    try {
                        jerseyNumber = Integer.parseInt(athlete.jersey());
                    } catch (NumberFormatException ex) {
                        // Rare non-numeric jerseys — skip rather than crash
                    }
                }

                // The athlete's individual position name (e.g. "Quarterback", "Wide Receiver")
                // comes from the nested position object, not the outer side-of-ball group.
                String positionName = (athlete.position() != null) ? athlete.position().name() : null;

                // Nationality proxy: birthPlace.country ("USA", "Canada", etc.)
                // ESPN doesn't expose citizenship directly — birthplace is the closest available field
                String country = (athlete.birthPlace() != null) ? athlete.birthPlace().country() : null;

                playerRepository.save(
                        Player.builder()
                                .team(team)
                                .name(athlete.fullName())   // e.g. "Patrick Mahomes"
                                .position(positionName)     // e.g. "Quarterback"
                                .nationality(country)       // e.g. "USA"
                                .jerseyNumber(jerseyNumber) // e.g. 15 — may be null in off-season
                                .dateOfBirth(null)          // Not provided by this ESPN endpoint
                                .build());
            }

            log.info("[NflDataLoader]     Saved {} players for {}", players.size(), team.getName());
        }

        log.info("[NflDataLoader] Done! NFL seeded with {} teams.", apiTeams.size());
    }
}
