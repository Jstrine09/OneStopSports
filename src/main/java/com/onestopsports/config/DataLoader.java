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
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

// DataLoader populates the database with real football data on first startup.
// Data comes from football-data.org via ExternalApiService.
//
// Seeding order: Sport → Leagues → Teams → Players
// Rate limit: football-data.org allows 10 requests/minute on the free tier,
// so we sleep ~6.2 seconds between each competition fetch. A full seed therefore
// takes roughly 10-15 minutes — the app is already serving while this runs in the
// background, and Futbol fills in league by league.
//
// Resumable & self-healing: the loader only seeds the football competitions that
// are missing (checked by their football-data.org externalId), so a partial seed
// is finished on the next restart rather than being skipped forever.
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

    // How long to sleep between rate-limited calls (free tier = 10 req/min → 6s minimum)
    private static final long RATE_LIMIT_SLEEP_MS = 6_200;

    // Max attempts for a single competition fetch when hitting transient errors (429 / 5xx)
    private static final int MAX_FETCH_ATTEMPTS = 3;

    private final ExternalApiService externalApiService;
    private final SportRepository    sportRepository;
    private final LeagueRepository   leagueRepository;
    private final TeamRepository     teamRepository;
    private final PlayerRepository   playerRepository;

    // Running totals for the end-of-seed summary
    private int leaguesSaved;
    private int teamsSaved;
    private int playersSaved;

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
        // Count how many of OUR football competitions are already in the DB (by their
        // football-data.org externalId). This is football-specific on purpose: counting
        // ALL leagues would mix in NBA/NFL and could wrongly decide football is "done"
        // when some football leagues are actually still missing.
        long alreadySeeded = Arrays.stream(COMPETITION_IDS)
                .filter(id -> leagueRepository.findByExternalId(id).isPresent())
                .count();

        if (alreadySeeded == COMPETITION_IDS.length) {
            log.info("[DataLoader] All {} football leagues already seeded — skipping.", COMPETITION_IDS.length);
            return;
        }
        if (alreadySeeded > 0) {
            log.info("[DataLoader] Resuming football seed — {}/{} leagues already present, fetching the rest.",
                    alreadySeeded, COMPETITION_IDS.length);
        }

        log.info("[DataLoader] ============================================================");
        log.info("[DataLoader] Seeding football data from football-data.org.");
        log.info("[DataLoader] Free tier = 10 req/min, so this sleeps between calls and");
        log.info("[DataLoader] takes ~10-15 min. The app is already up; Futbol fills in");
        log.info("[DataLoader] league by league as this runs in the background.");
        log.info("[DataLoader] ============================================================");

        long startMs = System.currentTimeMillis();
        try {
            seed();
            long mins = (System.currentTimeMillis() - startMs) / 60_000;
            log.info("[DataLoader] Done in ~{} min. Saved this run: {} leagues, {} teams, {} players.",
                    mins, leaguesSaved, teamsSaved, playersSaved);
        } catch (RestClientResponseException e) {
            // A non-2xx HTTP response from football-data.org. The most important case to
            // surface loudly is a bad/missing API key — otherwise it looks like a mystery
            // "Futbol is empty" bug with no obvious cause.
            int status = e.getStatusCode().value();
            if (isAuthError(e)) {
                log.error("[DataLoader] ############################################################");
                log.error("[DataLoader] FOOTBALL SEEDING ABORTED — football-data.org rejected the key.");
                log.error("[DataLoader] HTTP {}: {}", status, oneLine(e.getResponseBodyAsString()));
                log.error("[DataLoader] >> Set a valid FOOTBALL_DATA_API_KEY and restart. Until then,");
                log.error("[DataLoader] >> Futbol leagues/teams/scores will be EMPTY (NBA & NFL are");
                log.error("[DataLoader] >> unaffected — they use a keyless API).");
                log.error("[DataLoader] >> Free key: https://www.football-data.org/client/register");
                log.error("[DataLoader] ############################################################");
            } else if (status == 429) {
                log.error("[DataLoader] Football seeding hit the rate limit (HTTP 429) and stopped. " +
                        "Any leagues fetched so far are saved — restart to resume the rest.");
            } else {
                log.error("[DataLoader] Football seeding failed (HTTP {}): {}. " +
                        "Partial data may be saved — restart to resume.", status, oneLine(e.getResponseBodyAsString()));
            }
        } catch (Exception e) {
            log.error("[DataLoader] Football seeding failed — app still starts but Futbol may be " +
                    "incomplete. Restart to resume. Cause: {}", e.getMessage());
        }
    }

    private void seed() throws InterruptedException {

        // Find-or-create the Futbol sport — named "Futbol" (not "Football") to distinguish it
        // from American Football (NFL) which is a separate sport. The slug stays "football" so
        // existing API routes (/api/sports/football/leagues) keep working.
        // Idempotent: safe to call on every startup — skips creation if already present.
        Sport football = sportRepository.findBySlug("football")
                .orElseGet(() -> sportRepository.save(
                        Sport.builder()
                                .name("Futbol")
                                .slug("football")
                                .iconUrl("https://crests.football-data.org/FL.svg")
                                .build()));
        log.info("[DataLoader] Sport ready: {}", football.getName());

        // Loop through each competition ID and seed its league, teams, and players
        for (int i = 0; i < COMPETITION_IDS.length; i++) {
            int competitionId = COMPETITION_IDS[i];

            // Skip this league if it's already in the database (resume support).
            if (leagueRepository.findByExternalId(competitionId).isPresent()) {
                log.info("[DataLoader] League {}/{} (competition {}) already seeded — skipping.",
                        i + 1, COMPETITION_IDS.length, competitionId);
                continue;
            }

            log.info("[DataLoader] League {}/{}: fetching competition {}...",
                    i + 1, COMPETITION_IDS.length, competitionId);

            // Fetch all teams (and their squad lists) for this competition, retrying on
            // transient errors (rate-limit / server hiccups). Auth errors bubble straight up.
            ApiTeamsResponse response = fetchTeamsWithRetry(competitionId);

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
            leaguesSaved++;
            log.info("[DataLoader]   Saved league: {}", league.getName());

            List<ApiTeam> apiTeams = response.teams();
            int teamLimit = Math.min(MAX_TEAMS_PER_LEAGUE, apiTeams.size()); // Don't exceed our cap

            for (int j = 0; j < teamLimit; j++) {
                ApiTeam apiTeam = apiTeams.get(j);

                // Save the team. externalId = football-data.org integer team ID, stored as a
                // String for uniformity with ESPN IDs.
                Team team = teamRepository.save(
                        Team.builder()
                                .league(league)
                                .name(apiTeam.name())
                                .shortName(apiTeam.shortName())
                                .crestUrl(apiTeam.crest())
                                .stadium(apiTeam.venue())
                                .country(country)
                                .externalId(String.valueOf(apiTeam.id())) // football-data.org team ID → stored as String
                                .build());
                teamsSaved++;
                log.info("[DataLoader]     Saved team: {}", team.getName());

                // Try to get the squad from the competition response
                List<ApiPlayer> squad = apiTeam.squad();

                // Some competitions (especially UCL) don't include squad data in the competition
                // endpoint. In that case, make a separate API call to the team's own endpoint.
                if (squad == null || squad.isEmpty()) {
                    log.info("[DataLoader]       No squad in competition response for {}, fetching individually...", team.getName());
                    Thread.sleep(RATE_LIMIT_SLEEP_MS); // Sleep to stay within the 10 req/min rate limit
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
                        playersSaved++;
                    }
                    log.info("[DataLoader]       Saved {} players for {}", squad.size(), team.getName());
                }
            }

            // Sleep between competitions to stay within the API rate limit (10 requests per minute).
            // Skip the sleep after the last competition since there's no next request to delay.
            if (i < COMPETITION_IDS.length - 1) {
                log.info("[DataLoader] Waiting {}s (rate limit) before the next league...", RATE_LIMIT_SLEEP_MS / 1000);
                Thread.sleep(RATE_LIMIT_SLEEP_MS);
            }
        }
    }

    // Fetches a competition's teams, retrying only on TRANSIENT failures (HTTP 429 rate
    // limit or 5xx server errors) with an increasing back-off. Auth/other 4xx errors are
    // rethrown immediately so run() can surface them loudly — retrying a bad key is pointless.
    private ApiTeamsResponse fetchTeamsWithRetry(int competitionId) throws InterruptedException {
        int attempt = 0;
        while (true) {
            try {
                return externalApiService.fetchTeamsByCompetition(competitionId);
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                boolean isTransient = (status == 429 || status >= 500);
                attempt++;
                if (!isTransient || attempt >= MAX_FETCH_ATTEMPTS) {
                    throw e; // auth/permanent error, or out of retries → let run() handle it
                }
                long backoffMs = RATE_LIMIT_SLEEP_MS * 2L * attempt;
                log.warn("[DataLoader] Transient error (HTTP {}) fetching competition {} — " +
                                "retry {}/{} in {}s.",
                        status, competitionId, attempt, MAX_FETCH_ATTEMPTS - 1, backoffMs / 1000);
                Thread.sleep(backoffMs);
            }
        }
    }

    // True when the response looks like an authentication/authorisation problem with the API
    // key. football-data.org returns 401/403 for permission issues and, notably, 400 with
    // "Your API token is invalid." for a bad or missing key.
    private static boolean isAuthError(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        if (status == 401 || status == 403) {
            return true;
        }
        String body = e.getResponseBodyAsString();
        return status == 400 && body != null && body.toLowerCase().contains("token");
    }

    // Collapses a (possibly multi-line) response body to a single trimmed line, capped in
    // length, so error logs stay readable.
    private static String oneLine(String body) {
        if (body == null || body.isBlank()) {
            return "(no response body)";
        }
        String collapsed = body.replaceAll("\\s+", " ").trim();
        return collapsed.length() > 200 ? collapsed.substring(0, 200) + "…" : collapsed;
    }
}
