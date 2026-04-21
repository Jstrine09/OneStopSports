package com.matchday.config;

import com.matchday.model.League;
import com.matchday.model.Player;
import com.matchday.model.Sport;
import com.matchday.model.Team;
import com.matchday.repository.LeagueRepository;
import com.matchday.repository.PlayerRepository;
import com.matchday.repository.SportRepository;
import com.matchday.repository.TeamRepository;
import com.matchday.service.ExternalApiService;
import com.matchday.service.ExternalApiService.ApiPlayer;
import com.matchday.service.ExternalApiService.ApiTeam;
import com.matchday.service.ExternalApiService.ApiTeamsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Seeds the database with real football data from football-data.org on first startup.
 * Skips seeding if data already exists (idempotent).
 *
 * Ported and adapted from the OnesToManys project:
 *   - Added Sport as the top-level entity (MatchDay has Sport above League)
 *   - Stores dateOfBirth directly as LocalDate (vs computing age in OnesToManys)
 *   - Maps Club → Team, venue → stadium, shortName → shortName, crest → crestUrl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    // football-data.org competition IDs
    private static final int PL         = 2021; // Premier League
    private static final int LA_LIGA    = 2014; // La Liga
    private static final int BUNDESLIGA = 2002; // Bundesliga
    private static final int UCL        = 2001; // UEFA Champions League

    private static final int[] COMPETITION_IDS = {PL, LA_LIGA, BUNDESLIGA};

    private static final int MAX_TEAMS_PER_LEAGUE  = 20;
    private static final int MAX_PLAYERS_PER_TEAM  = 30;

    private final ExternalApiService externalApiService;
    private final SportRepository    sportRepository;
    private final LeagueRepository   leagueRepository;
    private final TeamRepository     teamRepository;
    private final PlayerRepository   playerRepository;

    @Override
    public void run(String... args) throws Exception {

        if (sportRepository.count() > 0) {
            log.info("[DataLoader] Database already seeded — skipping.");
            return;
        }

        log.info("[DataLoader] Seeding database from football-data.org...");

        // MatchDay adds a top-level Sport that OnesToManys didn't have
        Sport football = sportRepository.save(
                Sport.builder()
                        .name("Football")
                        .slug("football")
                        .iconUrl("https://crests.football-data.org/FL.svg")
                        .build());
        log.info("[DataLoader] Saved sport: {}", football.getName());

        for (int i = 0; i < COMPETITION_IDS.length; i++) {
            int competitionId = COMPETITION_IDS[i];
            log.info("[DataLoader] Fetching competition {}...", competitionId);

            ApiTeamsResponse response = externalApiService.fetchTeamsByCompetition(competitionId);

            // Resolve country (area can occasionally be null in the teams endpoint response)
            String country = (response.competition().area() != null)
                    ? response.competition().area().name()
                    : switch (competitionId) {
                        case PL         -> "England";
                        case LA_LIGA    -> "Spain";
                        case BUNDESLIGA -> "Germany";
                        default         -> "Unknown";
                    };

            League league = leagueRepository.save(
                    League.builder()
                            .sport(football)
                            .name(response.competition().name())
                            .country(country)
                            .season("2024/25")
                            .build());
            log.info("[DataLoader]   Saved league: {}", league.getName());

            List<ApiTeam> apiTeams = response.teams();
            int teamLimit = Math.min(MAX_TEAMS_PER_LEAGUE, apiTeams.size());

            for (int j = 0; j < teamLimit; j++) {
                ApiTeam apiTeam = apiTeams.get(j);

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

                if (apiTeam.squad() != null && !apiTeam.squad().isEmpty()) {
                    List<ApiPlayer> squad = apiTeam.squad();
                    int playerLimit = Math.min(MAX_PLAYERS_PER_TEAM, squad.size());

                    for (int k = 0; k < playerLimit; k++) {
                        ApiPlayer apiPlayer = squad.get(k);

                        LocalDate dob = null;
                        if (apiPlayer.dateOfBirth() != null) {
                            try {
                                dob = LocalDate.parse(apiPlayer.dateOfBirth());
                            } catch (Exception e) {
                                log.warn("[DataLoader] Could not parse dateOfBirth '{}' for player {}",
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
                    log.info("[DataLoader]       Saved {} players for {}", playerLimit, team.getName());
                }
            }

            // Respect the football-data.org free tier: 10 req/min → wait ~6 s between competitions
            if (i < COMPETITION_IDS.length - 1) {
                log.info("[DataLoader] Waiting 6 s (rate limit)...");
                Thread.sleep(6_200);
            }
        }

        log.info("[DataLoader] Done! Database seeded with real football data.");
    }
}
