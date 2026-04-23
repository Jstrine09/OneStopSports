package com.onestopsports.config;

import com.onestopsports.model.League;
import com.onestopsports.model.Player;
import com.onestopsports.model.Sport;
import com.onestopsports.model.Team;
import com.onestopsports.repository.LeagueRepository;
import com.onestopsports.repository.PlayerRepository;
import com.onestopsports.repository.SportRepository;
import com.onestopsports.repository.TeamRepository;
import com.onestopsports.service.ExternalApiService;
import com.onestopsports.service.ExternalApiService.ApiPlayer;
import com.onestopsports.service.ExternalApiService.ApiTeam;
import com.onestopsports.service.ExternalApiService.ApiTeamsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

// DataLoader populates the database with real football data on first startup.
// It only runs once — if all leagues are already in the database, it skips everything.
// Data comes from football-data.org via ExternalApiService.
//
// Seeding order: Sport → Leagues → Teams → Players
// Rate limit: football-data.org allows 10 requests/minute on the free tier,
// so we sleep 6.2 seconds between each competition fetch.
@Component // Marks this as a Spring-managed component so it gets picked up automatically
public class DataLoader implements CommandLineRunner { // CommandLineRunner means run() is called once at startup

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    // football-data.org competition IDs for the leagues we want to seed
    private static final int PL         = 2021; // Premier League (England)
    private static final int LA_LIGA    = 2014; // La Liga (Spain)
    private static final int BUNDESLIGA = 2002; // Bundesliga (Germany)
    private static final int SERIE_A    = 2019; // Serie A (Italy)
    private static final int LIGUE_1    = 2015; // Ligue 1 (France)
    private static final int UCL        = 2001; // UEFA Champions League

    // All competitions to seed — order matters because we sleep between each one
    private static final int[] COMPETITION_IDS = {PL, LA_LIGA, BUNDESLIGA, SERIE_A, LIGUE_1, UCL};

    // Cap on how many teams to save per league (some competitions list more than 20)
    private static final int MAX_TEAMS_PER_LEAGUE = 20;

    private final ExternalApiService externalApiService;
    private final SportRepository    sportRepository;
    private final LeagueRepository   leagueRepository;
    private final TeamRepository     teamRepository;
    private final PlayerRepository   playerRepository;

    public DataLoader(ExternalApiService externalApiService,
                    SportRepository sportRepository,
                    LeagueRepository leagueRepository,
                    TeamRepository teamRepository,
                    PlayerRepository playerRepository) {
        this.externalApiService = externalApiService;
        this.sportRepository    = sportRepository;
        this.leagueRepository   = leagueRepository;
        this.teamRepository     = teamRepository;
        this.playerRepository   = playerRepository;
    }

    @Override
    public void run(String... args) {
        // Check if all expected leagues are already in the database.
        // If we have fewer leagues than expected, some are missing — run the seeder.
        // This lets us add new leagues later without wiping and re-seeding from scratch.
        if (leagueRepository.count() >= COMPETITION_IDS.length) {
            log.info("[DataLoader] All {} leagues already seeded — skipping.", COMPETITION_IDS.length);
            return;
        }

        log.info("[DataLoader] Seeding database from football-data.org...");
        try {
            seed();
        } catch (Exception e) {
            // If something goes wrong (e.g. API is down), log the error but let the app start anyway.
            // Re-run the app to retry seeding.
            log.error("[DataLoader] Seeding failed — app will still start but DB will be empty. " +
                    "Re-run to retry. Cause: {}", e.getMessage());
        }
    }

    private void seed() throws InterruptedException {

        // Find-or-create the Football sport — idempotent so it doesn't crash if Football already exists
        // (which it will when we're only adding new leagues to an existing database)
        Sport football = sportRepository.findBySlug("football")
                .orElseGet(() -> sportRepository.save(
                        Sport.builder()
                                .name("Football")
                                .slug("football")
                                .iconUrl("https://crests.football-data.org/FL.svg")
                                .build()));
        log.info("[DataLoader] Saved sport: {}", football.getName());

        // Loop through each competition ID and seed its league, teams, and players
        for (int i = 0; i < COMPETITION_IDS.length; i++) {
            int competitionId = COMPETITION_IDS[i];
            log.info("[DataLoader] Fetching competition {}...", competitionId);

            // Skip this league if it's already in the database.
            // This means on a re-run we only fetch the new leagues, not the existing ones.
            if (leagueRepository.findByExternalId(competitionId).isPresent()) {
                log.info("[DataLoader] League {} already seeded, skipping.", competitionId);
                continue; // Jump to the next competition
            }

            // Fetch all teams (and their squad lists) for this competition from the API
            ApiTeamsResponse response = externalApiService.fetchTeamsByCompetition(competitionId);

            // Get the country name from the API response, or fall back to a hardcoded value
            String country = (response.competition().area() != null)
                    ? response.competition().area().name()
                    : switch (competitionId) {
                        case PL         -> "England";
                        case LA_LIGA    -> "Spain";
                        case BUNDESLIGA -> "Germany";
                        case SERIE_A    -> "Italy";
                        case LIGUE_1    -> "France";
                        case UCL        -> "Europe";
                        default         -> "Unknown";
                    };

            // Save the league to the database
            League league = leagueRepository.save(
                    League.builder()
                            .sport(football)
                            .externalId(competitionId) // Store the football-data.org ID for later API calls
                            .name(response.competition().name())
                            .country(country)
                            .season("2024/25")
                            .build());
            log.info("[DataLoader]   Saved league: {}", league.getName());

            List<ApiTeam> apiTeams = response.teams();
            int teamLimit = Math.min(MAX_TEAMS_PER_LEAGUE, apiTeams.size()); // Don't exceed our cap

            for (int j = 0; j < teamLimit; j++) {
                ApiTeam apiTeam = apiTeams.get(j);

                // Save the team to the database
                Team team = teamRepository.save(
                        Team.builder()
                                .league(league)
                                .name(apiTeam.name())
                                .shortName(apiTeam.shortName())
                                .crestUrl(apiTeam.crest())
                                .stadium(apiTeam.venue())
                                .country(country)
                                .build());
                log.info("[DataLoader]     Saved team: {}", team.getName());

                // Try to get the squad from the competition response
                List<ApiPlayer> squad = apiTeam.squad();

                // Some competitions (especially UCL) don't include squad data in the competition endpoint.
                // In that case, make a separate API call directly to the team's own endpoint.
                if (squad == null || squad.isEmpty()) {
                    log.info("[DataLoader]       No squad in competition response for {}, fetching individually...", team.getName());
                    Thread.sleep(6_200); // Sleep to stay within the 10 req/min rate limit
                    try {
                        ApiTeam fullTeam = externalApiService.fetchTeamById(apiTeam.id());
                        squad = (fullTeam != null) ? fullTeam.squad() : null;
                    } catch (Exception e) {
                        log.warn("[DataLoader]       Could not fetch team {}: {}", team.getName(), e.getMessage());
                    }
                }

                // Save each player on the squad to the database
                if (squad != null && !squad.isEmpty()) {
                    for (ApiPlayer apiPlayer : squad) {
                        // Parse the date of birth string into a LocalDate — log a warning if it fails
                        LocalDate dob = null;
                        if (apiPlayer.dateOfBirth() != null) {
                            try {
                                dob = LocalDate.parse(apiPlayer.dateOfBirth()); // e.g. "1990-06-23"
                            } catch (Exception e) {
                                log.warn("[DataLoader] Could not parse dateOfBirth '{}' for {}",
                                        apiPlayer.dateOfBirth(), apiPlayer.name());
                            }
                        }

                        playerRepository.save(
                                Player.builder()
                                        .team(team)
                                        .name(apiPlayer.name())
                                        .position(apiPlayer.position())
                                        .jerseyNumber(apiPlayer.shirtNumber())
                                        .nationality(apiPlayer.nationality())
                                        .dateOfBirth(dob)
                                        .build());
                    }
                    log.info("[DataLoader]       Saved {} players for {}", squad.size(), team.getName());
                }
            }

            // Sleep between competitions to stay within the API rate limit (10 requests per minute).
            // Skip the sleep after the last competition since there's no next request to delay.
            if (i < COMPETITION_IDS.length - 1) {
                log.info("[DataLoader] Waiting 6 s (rate limit)...");
                Thread.sleep(6_200);
            }
        }

        log.info("[DataLoader] Done! Database seeded with real football data.");
    }
}
