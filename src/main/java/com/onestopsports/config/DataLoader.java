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

@Component
public class DataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    private static final int PL         = 2021;
    private static final int LA_LIGA    = 2014;
    private static final int BUNDESLIGA = 2002;
    private static final int SERIE_A    = 2019;
    private static final int LIGUE_1    = 2015;
    private static final int UCL        = 2001;

    private static final int[] COMPETITION_IDS = {PL, LA_LIGA, BUNDESLIGA, SERIE_A, LIGUE_1, UCL};

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
        if (leagueRepository.count() >= COMPETITION_IDS.length) {
            log.info("[DataLoader] All {} leagues already seeded — skipping.", COMPETITION_IDS.length);
            return;
        }
        log.info("[DataLoader] Seeding database from football-data.org...");
        try {
            seed();
        } catch (Exception e) {
            log.error("[DataLoader] Seeding failed — app will still start but DB will be empty. " +
                    "Re-run to retry. Cause: {}", e.getMessage());
        }
    }

    private void seed() throws InterruptedException {

        Sport football = sportRepository.findBySlug("football")
                .orElseGet(() -> sportRepository.save(
                        Sport.builder()
                                .name("Football")
                                .slug("football")
                                .iconUrl("https://crests.football-data.org/FL.svg")
                                .build()));
        log.info("[DataLoader] Saved sport: {}", football.getName());

        for (int i = 0; i < COMPETITION_IDS.length; i++) {
            int competitionId = COMPETITION_IDS[i];
            log.info("[DataLoader] Fetching competition {}...", competitionId);

            if (leagueRepository.findByExternalId(competitionId).isPresent()) {
                log.info("[DataLoader] League {} already seeded, skipping.", competitionId);
                continue;
            }

            ApiTeamsResponse response = externalApiService.fetchTeamsByCompetition(competitionId);

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

            League league = leagueRepository.save(
                    League.builder()
                            .sport(football)
                            .externalId(competitionId)
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

                List<ApiPlayer> squad = apiTeam.squad();
                if (squad == null || squad.isEmpty()) {
                    log.info("[DataLoader]       No squad in competition response for {}, fetching individually...", team.getName());
                    Thread.sleep(6_200);
                    try {
                        ApiTeam fullTeam = externalApiService.fetchTeamById(apiTeam.id());
                        squad = (fullTeam != null) ? fullTeam.squad() : null;
                    } catch (Exception e) {
                        log.warn("[DataLoader]       Could not fetch team {}: {}", team.getName(), e.getMessage());
                    }
                }

                if (squad != null && !squad.isEmpty()) {
                    for (ApiPlayer apiPlayer : squad) {
                        LocalDate dob = null;
                        if (apiPlayer.dateOfBirth() != null) {
                            try {
                                dob = LocalDate.parse(apiPlayer.dateOfBirth());
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

            if (i < COMPETITION_IDS.length - 1) {
                log.info("[DataLoader] Waiting 6 s (rate limit)...");
                Thread.sleep(6_200);
            }
        }
        log.info("[DataLoader] Done! Database seeded with real football data.");
    }
}
