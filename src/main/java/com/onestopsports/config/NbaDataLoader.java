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

// NbaDataLoader seeds the database with NBA teams and rosters on first startup.
// It runs independently from the football DataLoader — the two don't know about each other.
//
// Data source: balldontlie.io v1 API (free tier, Bearer token auth)
// Seeding order: Sport → League → Teams → Players
//
// Why separate from DataLoader.java?
// Keeping NBA seeding in its own class makes it easier to maintain, test, and extend
// without touching the football seeding logic. Each sport owns its own loader.
@Component
public class NbaDataLoader implements CommandLineRunner { // CommandLineRunner = runs once at startup

    private static final Logger log = LoggerFactory.getLogger(NbaDataLoader.class);

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
        // Skip seeding if the Basketball sport already exists AND has at least one league.
        // This means seeding ran successfully before — no need to do it again.
        boolean alreadySeeded = sportRepository.findBySlug("basketball")
                .map(s -> !leagueRepository.findBySportId(s.getId()).isEmpty())
                .orElse(false);

        if (alreadySeeded) {
            log.info("[NbaDataLoader] NBA already seeded — skipping.");
            return;
        }

        log.info("[NbaDataLoader] Seeding NBA data from balldontlie.io...");
        try {
            seed();
        } catch (Exception e) {
            // If something goes wrong (API down, rate limited, etc.), log and move on.
            // The app still starts — re-run to retry seeding.
            log.error("[NbaDataLoader] Seeding failed — app will start but NBA data will be missing. " +
                    "Re-run to retry. Cause: {}", e.getMessage());
        }
    }

    private void seed() {
        // ── 1. Sport ──────────────────────────────────────────────────────────
        // Find-or-create the Basketball sport. Using findBySlug first means this is
        // safe to call even if Basketball was partially created in a previous failed run.
        Sport basketball = sportRepository.findBySlug("basketball")
                .orElseGet(() -> sportRepository.save(
                        Sport.builder()
                                .name("Basketball")
                                .slug("basketball")
                                // 🏀 icon hosted by Wikipedia Commons — no auth required
                                .iconUrl("https://upload.wikimedia.org/wikipedia/commons/thumb/7/7a/Basketball.png/240px-Basketball.png")
                                .build()));
        log.info("[NbaDataLoader] Saved sport: {}", basketball.getName());

        // ── 2. League ─────────────────────────────────────────────────────────
        // Create the NBA as a single league under Basketball.
        // externalId is null — balldontlie doesn't use competition IDs like football-data.org.
        // Routing in MatchService uses the sport slug ("basketball") instead.
        League nba = leagueRepository.save(
                League.builder()
                        .sport(basketball)
                        .name("NBA")
                        .country("United States")
                        .season("2024-25")
                        .externalId(null) // No external competition ID for basketball
                        .build());
        log.info("[NbaDataLoader] Saved league: {}", nba.getName());

        // ── 3. Teams ──────────────────────────────────────────────────────────
        // Fetch all 30 NBA teams from balldontlie — they come back in a single response
        NbaApiService.NbaTeamsResponse teamsResponse = nbaApiService.fetchAllTeams();

        if (teamsResponse == null || teamsResponse.data() == null) {
            log.warn("[NbaDataLoader] No teams returned from API — aborting.");
            return;
        }

        List<NbaTeam> apiTeams = teamsResponse.data();
        log.info("[NbaDataLoader] Fetched {} teams from balldontlie", apiTeams.size());

        for (NbaTeam apiTeam : apiTeams) {
            // Save the team to the database
            // Note: crestUrl and stadium are null — balldontlie free tier doesn't provide them
            Team team = teamRepository.save(
                    Team.builder()
                            .league(nba)
                            .name(apiTeam.fullName())       // e.g. "Boston Celtics"
                            .shortName(apiTeam.abbreviation()) // e.g. "BOS"
                            .country(apiTeam.city())        // e.g. "Boston" (closest to country we have)
                            .crestUrl(null)                 // Not available in free tier
                            .stadium(null)                  // Not available in free tier
                            .build());
            log.info("[NbaDataLoader]   Saved team: {}", team.getName());

            // ── 4. Players ────────────────────────────────────────────────────
            // Fetch the full roster for this team — balldontlie paginates players,
            // so fetchPlayersByTeam handles the cursor loop internally
            List<NbaPlayer> players = nbaApiService.fetchPlayersByTeam(apiTeam.id());

            for (NbaPlayer apiPlayer : players) {
                // Parse the jersey number — it's a String in balldontlie (e.g. "23")
                // but our DB stores it as an Integer
                Integer jerseyNumber = null;
                if (apiPlayer.jerseyNumber() != null && !apiPlayer.jerseyNumber().isBlank()) {
                    try {
                        jerseyNumber = Integer.parseInt(apiPlayer.jerseyNumber());
                    } catch (NumberFormatException ex) {
                        // Some non-numeric jerseys exist — skip rather than crash
                        log.warn("[NbaDataLoader] Could not parse jersey number '{}' for {}",
                                apiPlayer.jerseyNumber(), apiPlayer.firstName());
                    }
                }

                // Combine first and last name into a single display name
                String fullName = (apiPlayer.firstName() + " " + apiPlayer.lastName()).trim();

                playerRepository.save(
                        Player.builder()
                                .team(team)
                                .name(fullName)
                                // Map the abbreviated position to the full name:
                                // "G" → "Guard", "F" → "Forward", "C" → "Center", etc.
                                .position(mapPosition(apiPlayer.position()))
                                .nationality(apiPlayer.country()) // e.g. "USA"
                                .jerseyNumber(jerseyNumber)
                                .dateOfBirth(null) // balldontlie free tier doesn't include DOB
                                .build());
            }

            log.info("[NbaDataLoader]     Saved {} players for {}", players.size(), team.getName());
        }

        log.info("[NbaDataLoader] Done! NBA seeded with {} teams.", apiTeams.size());
    }

    // Maps balldontlie's abbreviated position codes to the full position names
    // stored in the database. These full names are used by the frontend to group
    // players into sections on the team detail page.
    private String mapPosition(String pos) {
        if (pos == null || pos.isBlank()) return null;
        return switch (pos) {
            case "G"   -> "Guard";
            case "F"   -> "Forward";
            case "C"   -> "Center";
            case "G-F" -> "Guard-Forward";
            case "F-C" -> "Forward-Center";
            default    -> pos; // Return as-is if it's an unexpected value
        };
    }
}
