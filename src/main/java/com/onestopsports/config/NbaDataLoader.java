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
import com.onestopsports.service.NbaApiService.EspnAthlete;
import com.onestopsports.service.NbaApiService.EspnTeam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// NbaDataLoader seeds the database with NBA teams and rosters on first startup.
// It runs independently from the football DataLoader and NflDataLoader — they don't coordinate.
//
// Data source: ESPN's unofficial public NBA API (no API key required)
// Endpoint pattern: https://site.api.espn.com/apis/site/v2/sports/basketball/nba/...
//
// Seeding order:
//   1. Sport ("Basketball", slug "basketball")
//   2. League ("NBA")
//   3. 30 teams (with crest logo URLs from ESPN's CDN)
//   4. Roster for each team (~15 active players — ESPN returns the current-season roster)
//
// Idempotency / re-seed logic:
//   "Fully seeded" = all 30 teams exist AND have logos AND all have players.
//   This means:
//   - New install: seeds everything from scratch
//   - Old balldontlie data (no logos): updates all 30 crestUrls, skips players (they exist)
//   - After deleting NBA players (roster refresh): teams already have logos, re-seeds only players
//   - All three cases are handled transparently on the next startup
@Component
public class NbaDataLoader implements CommandLineRunner { // CommandLineRunner = runs once at startup

    private static final Logger log = LoggerFactory.getLogger(NbaDataLoader.class);

    // There are exactly 30 NBA teams — used as the "fully seeded" marker
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
        // "Fully seeded" = 30 teams exist, at least one has a crest URL (ESPN-sourced),
        // AND every team has at least one player.
        // If any of these fail, we enter the seed loop to fix what's missing.
        boolean fullySeeded = sportRepository.findBySlug("basketball")
                .flatMap(s -> leagueRepository.findBySportId(s.getId())
                        .stream()
                        .filter(l -> "NBA".equals(l.getName()))
                        .findFirst())
                .map(l -> {
                    List<Team> teams = teamRepository.findByLeagueId(l.getId());
                    if (teams.size() < NBA_TEAM_COUNT) return false; // Not all 30 teams exist yet

                    // Verify logos are populated (ESPN data) and every team has players in the DB
                    boolean hasLogos   = teams.stream().anyMatch(t -> t.getCrestUrl() != null);
                    boolean hasPlayers = teams.stream().allMatch(t ->
                            playerRepository.countByTeamId(t.getId()) > 0);
                    return hasLogos && hasPlayers;
                })
                .orElse(false);

        if (fullySeeded) {
            log.info("[NbaDataLoader] All {} NBA teams fully seeded — skipping.", NBA_TEAM_COUNT);
            return;
        }

        log.info("[NbaDataLoader] Seeding/updating NBA data from ESPN...");
        try {
            seed();
        } catch (Exception e) {
            // If something goes wrong (network error, ESPN API change), log and move on.
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
        // Find the NBA league if it already exists (from a previous run),
        // or create it fresh. externalId is null — ESPN has no competition ID.
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
                                .externalId(null) // No external competition ID — routing by sport slug
                                .build()));
        log.info("[NbaDataLoader] League: {}", nba.getName());

        // ── 3. Teams ──────────────────────────────────────────────────────────
        // ESPN returns teams nested under: sports[0].leagues[0].teams[*].team
        NbaApiService.EspnTeamsResponse teamsResponse = nbaApiService.fetchAllTeams();

        if (teamsResponse == null || teamsResponse.sports() == null || teamsResponse.sports().isEmpty()) {
            log.warn("[NbaDataLoader] No teams returned from ESPN — aborting.");
            return;
        }

        // Navigate down the nested ESPN response to get the flat team list
        List<NbaApiService.EspnLeague> leagues = teamsResponse.sports().get(0).leagues();
        if (leagues == null || leagues.isEmpty()) {
            log.warn("[NbaDataLoader] No leagues in ESPN response — aborting.");
            return;
        }

        // Extract the list of EspnTeam objects from the wrapper entries
        List<EspnTeam> apiTeams = leagues.get(0).teams()
                .stream()
                .map(entry -> entry.team())  // Each entry is { "team": {...} } — unwrap
                .filter(team -> team != null)
                .toList();

        log.info("[NbaDataLoader] Fetched {} teams from ESPN", apiTeams.size());

        // Build a name → Team entity map for quick existing-team lookups
        Map<String, Team> existingTeamsByName = teamRepository.findByLeagueId(nba.getId())
                .stream()
                .collect(Collectors.toMap(Team::getName, t -> t));

        for (EspnTeam apiTeam : apiTeams) {

            // Get the first logo URL (default light-mode version from ESPN's CDN)
            String crestUrl = (apiTeam.logos() != null && !apiTeam.logos().isEmpty())
                    ? apiTeam.logos().get(0).href()
                    : null;

            Team team;

            if (existingTeamsByName.containsKey(apiTeam.displayName())) {
                // ── Existing team ──────────────────────────────────────────
                team = existingTeamsByName.get(apiTeam.displayName());

                // Update the crest URL if it was null (migration from old balldontlie data)
                if (team.getCrestUrl() == null && crestUrl != null) {
                    team.setCrestUrl(crestUrl);
                    teamRepository.save(team);
                    log.info("[NbaDataLoader]   Updated crest URL for {}", team.getName());
                }

                // If this team already has players in the DB, skip roster seeding
                // (we only re-seed players when the DB has been cleared for a refresh)
                if (playerRepository.countByTeamId(team.getId()) > 0) {
                    log.info("[NbaDataLoader]   {} already seeded, skipping roster.", team.getName());
                    continue;
                }

                // Team exists but has zero players — fall through to seed the roster.
                // This happens when someone deletes NBA players from the DB to force a refresh.
                log.info("[NbaDataLoader]   {} has no players — seeding roster from ESPN...", team.getName());

            } else {
                // ── New team ───────────────────────────────────────────────
                // First time we've seen this team — save it with the ESPN logo
                team = teamRepository.save(
                        Team.builder()
                                .league(nba)
                                .name(apiTeam.displayName())   // e.g. "Boston Celtics"
                                .shortName(apiTeam.abbreviation()) // e.g. "BOS"
                                .country(apiTeam.location())   // e.g. "Boston" (city)
                                .crestUrl(crestUrl)             // ESPN CDN logo URL
                                .stadium(null)                  // Not provided by ESPN teams endpoint
                                .build());
                log.info("[NbaDataLoader]   Saved team: {}", team.getName());
            }

            // ── 4. Players ────────────────────────────────────────────────────
            // Brief courtesy sleep between roster fetches — ESPN has no documented rate limit
            // but a small pause avoids hammering their servers. 500ms × 30 teams ≈ 15s.
            Thread.sleep(500);

            // fetchPlayersByTeam returns a flat list — no grouping to flatten (unlike NFL)
            List<EspnAthlete> players = nbaApiService.fetchPlayersByTeam(apiTeam.id());

            for (EspnAthlete athlete : players) {
                // Jersey number is a String from ESPN — parse to Integer for our DB
                Integer jerseyNumber = null;
                if (athlete.jersey() != null && !athlete.jersey().isBlank()) {
                    try {
                        jerseyNumber = Integer.parseInt(athlete.jersey());
                    } catch (NumberFormatException ex) {
                        // Non-numeric jersey (e.g. "00") — ignore rather than crash
                    }
                }

                // ESPN provides full position names ("Center", "Guard", "Forward") — no mapping needed
                String positionName = (athlete.position() != null) ? athlete.position().name() : null;

                // Nationality proxy: birthPlace.country e.g. "USA", "Australia", "France"
                String country = (athlete.birthPlace() != null) ? athlete.birthPlace().country() : null;

                // ESPN provides date of birth as ISO-8601 e.g. "1984-12-30T07:00Z"
                // We only need the date part — take the first 10 characters "YYYY-MM-DD"
                LocalDate dateOfBirth = null;
                if (athlete.dateOfBirth() != null && athlete.dateOfBirth().length() >= 10) {
                    try {
                        dateOfBirth = LocalDate.parse(athlete.dateOfBirth().substring(0, 10));
                    } catch (Exception ignored) {
                        // Malformed date string — leave null
                    }
                }

                playerRepository.save(
                        Player.builder()
                                .team(team)
                                .name(athlete.fullName())   // e.g. "LeBron James"
                                .position(positionName)     // e.g. "Center" (full name from ESPN)
                                .nationality(country)       // e.g. "USA"
                                .jerseyNumber(jerseyNumber) // e.g. 23
                                .dateOfBirth(dateOfBirth)   // e.g. 1984-12-30
                                .build());
            }

            log.info("[NbaDataLoader]     Saved {} players for {}", players.size(), team.getName());
        }

        log.info("[NbaDataLoader] Done! NBA seeding complete for {} teams.", apiTeams.size());
    }
}
