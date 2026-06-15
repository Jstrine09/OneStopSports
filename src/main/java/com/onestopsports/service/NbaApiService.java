package com.onestopsports.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.onestopsports.dto.BoxScoreDto;
import com.onestopsports.dto.PlayerDto;
import com.onestopsports.dto.MatchDto;
import com.onestopsports.dto.PlayerCareerStatsDto;
import com.onestopsports.dto.StandingsEntryDto;
import com.onestopsports.dto.TeamDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

// This service talks to ESPN's unofficial public NBA API — no API key required.
//
// Two base URLs are used because standings live on a different ESPN host:
//   Main API:      https://site.api.espn.com/apis/site/v2/sports/basketball/nba
//   Standings API: https://site.web.api.espn.com/apis/v2/sports/basketball/nba
//
// Previously this service used balldontlie.io. We switched to ESPN because:
//   - ESPN provides team logos on the free tier (balldontlie doesn't)
//   - ESPN standings work without a paid subscription (balldontlie standings require paid)
//   - Same ESPN pattern as NflApiService — more consistent codebase
//
// Key differences from NflApiService (our other ESPN service):
//   - Roster response: athletes is a FLAT array, not grouped by offense/defense/specialTeam
//   - Scoreboard teams have a single "logo" string, not a "logos" array
//   - Standings are split by conference (East/West), not conference+division like NFL
@Service
public class NbaApiService {

    private static final Logger log = LoggerFactory.getLogger(NbaApiService.class);

    // Main ESPN API client — used for teams, rosters, and scoreboard
    private final RestClient restClient;

    // Separate client for the standings endpoint which lives on a different ESPN subdomain
    private final RestClient standingsClient;

    // Third client for the career-stats endpoint — yet another ESPN path (common/v3).
    private final RestClient statsClient;

    // All three base URLs are injected from application.yml so they can be overridden in tests.
    // No API key is needed — ESPN's unofficial API is publicly accessible.
    // @Autowired is required here because we also have a package-private test constructor below —
    // Spring needs to know which constructor to use for production dependency injection.
    @org.springframework.beans.factory.annotation.Autowired
    public NbaApiService(
            @Value("${external-api.nba.base-url}") String baseUrl,
            @Value("${external-api.nba.standings-url}") String standingsUrl,
            @Value("${external-api.nba.stats-url}") String statsUrl) {
        // No Authorization header needed — ESPN API is publicly accessible
        this.restClient       = RestClient.builder().baseUrl(baseUrl).build();
        this.standingsClient  = RestClient.builder().baseUrl(standingsUrl).build();
        this.statsClient      = RestClient.builder().baseUrl(statsUrl).build();
    }

    // Package-private test constructor — accepts pre-built RestClient instances.
    // Used by NbaApiServiceTest so we can inject mock clients without starting a real HTTP server.
    // Never called by Spring — only by unit tests in the same package.
    NbaApiService(RestClient restClient, RestClient standingsClient, RestClient statsClient) {
        this.restClient      = restClient;
        this.standingsClient = standingsClient;
        this.statsClient     = statsClient;
    }

    // ── API Response Records ──────────────────────────────────────────────────
    // These inner records mirror ESPN's JSON response structure.
    // @JsonIgnoreProperties(ignoreUnknown = true) means extra fields in ESPN's
    // response won't crash the app — we just ignore what we don't need.
    // The nesting for teams is deliberately deep: sports → leagues → teams → team.

    @JsonIgnoreProperties(ignoreUnknown = true)
    // Top-level wrapper for GET /teams — contains sports → leagues → teams
    public record EspnTeamsResponse(List<EspnSport> sports) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EspnSport(List<EspnLeague> leagues) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EspnLeague(List<EspnTeamEntry> teams) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    // Each team in the list is wrapped in a { "team": {...} } object — we unwrap it
    public record EspnTeamEntry(EspnTeam team) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    // One NBA team — e.g. { "id": "1", "displayName": "Atlanta Hawks", "abbreviation": "ATL", ... }
    public record EspnTeam(
            String id,            // ESPN's string team ID — e.g. "1" (not our DB ID)
            String displayName,   // Full name — e.g. "Atlanta Hawks"
            String abbreviation,  // e.g. "ATL" — shown in score cards
            String location,      // City — e.g. "Atlanta"
            String name,          // Short name — e.g. "Hawks"
            List<EspnLogo> logos) {} // Team logo URLs — first is the default light-mode logo

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EspnLogo(String href) {} // URL to the logo image on ESPN's CDN

    @JsonIgnoreProperties(ignoreUnknown = true)
    // Response for GET /teams/{id}/roster
    // NBA rosters use a FLAT athletes array — unlike NFL which groups by offense/defense/specialTeam
    public record EspnRosterResponse(List<EspnAthlete> athletes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    // One NBA player — name, jersey, position, birthplace, and date of birth
    public record EspnAthlete(
            String id,                    // ESPN athlete ID
            String fullName,              // e.g. "LeBron James"
            String jersey,                // Jersey number as string — e.g. "23" (may be null)
            String dateOfBirth,           // ISO-8601 string — e.g. "1984-12-30T07:00Z"
            EspnAthletePosition position, // The player's specific position (nested object)
            EspnBirthPlace birthPlace) {} // Birthplace — used as nationality proxy

    @JsonIgnoreProperties(ignoreUnknown = true)
    // The player's playing position
    public record EspnAthletePosition(
            String name,          // Full name — e.g. "Center", "Guard", "Forward"
            String abbreviation)  // Short code — e.g. "C", "G", "F"
    {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EspnBirthPlace(
            String city,    // e.g. "Akron"
            String country) // e.g. "USA" — used as a nationality proxy
    {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    // Response for GET /scoreboard?dates=YYYYMMDD — list of games on that date
    public record EspnScoreboardResponse(List<EspnEvent> events) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    // One NBA game event — contains status, both teams, and scores
    public record EspnEvent(
            String id,                          // ESPN event ID — string like "401705649"
            String date,                        // ISO-8601 UTC string — e.g. "2025-04-20T17:00Z"
            EspnEventStatus status,             // Game status (scheduled, in progress, final, etc.)
            List<EspnCompetition> competitions) // Always contains exactly one competition
    {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EspnEventStatus(
            EspnStatusType type,
            String displayClock, // Live game clock e.g. "4:12" (in-period time remaining)
            Integer period)      // Quarter number (1-4, 5+ = overtime)
    {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EspnStatusType(
            String name,         // Machine-readable: "STATUS_FINAL", "STATUS_IN_PROGRESS", etc.
            String description)  // Human-readable: "Final", "In Progress", "7:00 PM ET"
    {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    // A competition is ESPN's term for one scheduled match — a game between two teams
    public record EspnCompetition(List<EspnCompetitor> competitors) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    // One side in a game — either the home or away team with their score
    public record EspnCompetitor(
            String homeAway,     // "home" or "away"
            EspnCompTeam team,   // The team playing
            String score) {}     // Score as a string — empty string before the game starts

    @JsonIgnoreProperties(ignoreUnknown = true)
    // Condensed team info as it appears inside a scoreboard event.
    // NBA scoreboard uses a single "logo" string (not the "logos" array used by the teams endpoint)
    public record EspnCompTeam(
            String id,
            String displayName,   // e.g. "Oklahoma City Thunder"
            String abbreviation,  // e.g. "OKC"
            String logo) {}       // Single logo URL string (not an array like the teams endpoint)

    @JsonIgnoreProperties(ignoreUnknown = true)
    // Response for GET /standings?season=YYYY — two conference children (East + West)
    public record EspnStandingsResponse(List<EspnConference> children) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    // One NBA conference — "Eastern Conference" or "Western Conference"
    // NBA standings go: Conference → Entries (no division level, unlike NFL)
    public record EspnConference(
            String name,                     // "Eastern Conference" or "Western Conference"
            EspnStandingsSection standings)  // The actual standings data for this conference
    {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    // Contains the entries list — separated out because ESPN nests it under a "standings" key
    public record EspnStandingsSection(List<EspnStandingsEntry> entries) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    // One row in the standings — one team's season record
    public record EspnStandingsEntry(
            EspnStandingsTeam team,   // The team
            List<EspnStat> stats) {}  // Stats including "wins", "losses", "playoffSeed", etc.

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EspnStandingsTeam(
            String id,
            String displayName,   // e.g. "Cleveland Cavaliers"
            String abbreviation,  // e.g. "CLE"
            String location) {}   // City — e.g. "Cleveland"

    @JsonIgnoreProperties(ignoreUnknown = true)
    // A named stat — we specifically use "wins", "losses", and "playoffSeed"
    public record EspnStat(
            String name,          // e.g. "wins", "losses", "playoffSeed"
            Double value,         // Numeric value — e.g. 64.0
            String displayValue)  // Formatted string — e.g. "64"
    {}

    // ── Career stats response records ─────────────────────────────────────────
    // GET /athletes/{id}/stats returns 3 categories ("averages", "totals", "miscellaneous"),
    // each shaped: { labels[], statistics[ {season, teamSlug, stats[]} ], totals[] }.

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EspnStatsResponse(List<EspnStatCategory> categories) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EspnStatCategory(
            String name,                          // "averages" | "totals" | "miscellaneous"
            String displayName,                   // "Averages" | "Totals" | "Miscellaneous"
            List<String> labels,                  // column headers — e.g. ["GP", "MIN", "PTS", ...]
            List<EspnStatEntry> statistics,       // one row per season-team
            List<String> totals)                  // career aggregate row — aligned with labels[]
    {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EspnStatEntry(
            String teamSlug,                      // e.g. "los-angeles-lakers" — may be null
            String teamAbbreviation,              // not always present in ESPN payload — kept for forward-compat
            EspnStatSeason season,                // year + displayName
            List<String> stats)                   // values aligned with parent category's labels[]
    {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EspnStatSeason(
            Integer year,                         // ending year — e.g. 2025 for the "2024-25" season
            String displayName)                   // human label — e.g. "2024-25"
    {}

    // ── Public API Methods ────────────────────────────────────────────────────

    /**
     * Fetches all 30 NBA teams from ESPN.
     * ESPN returns all teams in a single response under a deeply nested path:
     * sports[0].leagues[0].teams[*].team
     * Used by NbaDataLoader at startup to seed the database.
     */
    public EspnTeamsResponse fetchAllTeams() {
        return restClient.get()
                .uri("/teams?limit=32") // 30 NBA teams — request slightly more to be safe
                .retrieve()
                .body(EspnTeamsResponse.class);
    }

    /**
     * Fetches the roster for a single NBA team by its ESPN team ID.
     *
     * Unlike NFL (where players are grouped by offense/defense/specialTeam),
     * NBA rosters return a flat "athletes" list — no groups to flatten.
     *
     * @param espnTeamId ESPN's string team ID — e.g. "1" for the Atlanta Hawks
     */
    public List<EspnAthlete> fetchPlayersByTeam(String espnTeamId) {
        EspnRosterResponse response = restClient.get()
                .uri("/teams/{id}/roster", espnTeamId) // e.g. /teams/1/roster
                .retrieve()
                .body(EspnRosterResponse.class);

        if (response == null || response.athletes() == null) return Collections.emptyList();

        // NBA roster is already a flat list — no grouping to flatten (unlike NFL)
        return response.athletes();
    }

    /**
     * Fetches a historical NBA roster for a given season, mapped directly to PlayerDtos.
     *
     * Called by TeamService when the frontend requests a past season's roster.
     * Uses the same ESPN endpoint as fetchPlayersByTeam but adds the ?season= query param.
     * ESPN's season parameter is the START year of the season — e.g. 2022 for "2022-23".
     *
     * Returned PlayerDtos have null id and teamId because historical players may not
     * exist in our database — the frontend treats them as display-only (no profile links).
     *
     * @param espnTeamId ESPN's string team ID — stored as Team.externalId since V7
     * @param season     Start year of the season — e.g. 2022 for "2022-23"
     */
    public List<PlayerDto> fetchRosterDtos(String espnTeamId, Integer season) {
        try {
            EspnRosterResponse response = restClient.get()
                    .uri("/teams/{id}/roster?season={season}", espnTeamId, season)
                    .retrieve()
                    .body(EspnRosterResponse.class);

            if (response == null || response.athletes() == null) return Collections.emptyList();

            // Map each ESPN athlete to a PlayerDto — same field mapping as the data loader,
            // but we output PlayerDtos instead of persisting Player entities.
            List<PlayerDto> result = new ArrayList<>();
            for (EspnAthlete athlete : response.athletes()) {
                result.add(espnAthleteToDto(athlete));
            }
            return result;

        } catch (RestClientException e) {
            // ESPN may return 400/404 for seasons before the team existed or very old data.
            // Return empty rather than crashing — the frontend shows a "no data" message.
            log.warn("[NbaApiService] Could not fetch roster for team {} season {}: {}",
                    espnTeamId, season, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Maps an ESPN athlete record to a PlayerDto for display without DB involvement.
     * id and teamId are null — the player may not exist in our database (historical seasons).
     * photoUrl uses the deterministic ESPN CDN headshot pattern.
     */
    private PlayerDto espnAthleteToDto(EspnAthlete athlete) {
        // Jersey number comes as a String (e.g. "23") — parse to Integer, ignore if blank/non-numeric
        Integer jerseyNumber = null;
        if (athlete.jersey() != null && !athlete.jersey().isBlank()) {
            try { jerseyNumber = Integer.parseInt(athlete.jersey()); } catch (NumberFormatException ignored) {}
        }

        // Date of birth is ISO-8601 — e.g. "1984-12-30T07:00Z". We only need the date part.
        LocalDate dateOfBirth = null;
        if (athlete.dateOfBirth() != null && athlete.dateOfBirth().length() >= 10) {
            try { dateOfBirth = LocalDate.parse(athlete.dateOfBirth().substring(0, 10)); } catch (Exception ignored) {}
        }

        String position = (athlete.position() != null) ? athlete.position().name() : null;
        String country  = (athlete.birthPlace() != null) ? athlete.birthPlace().country() : null;

        // ESPN NBA headshot URL — deterministic, no API call needed.
        // Same pattern used by PlayerService.resolvePhotoUrl for current-season players.
        String photoUrl = (athlete.id() != null)
                ? "https://a.espncdn.com/i/headshots/nba/players/full/" + athlete.id() + ".png"
                : null;

        // id=null, teamId=null: historical player rows aren't linked to DB records.
        // The frontend checks for null id to disable the "view profile" link.
        return new PlayerDto(null, athlete.fullName(), position, country, dateOfBirth, jerseyNumber, photoUrl, null);
    }

    /**
     * Fetches NBA games on a specific date and converts them to MatchDtos.
     *
     * ESPN's scoreboard uses a different date format: YYYYMMDD with no dashes
     * (e.g. "20250420") — the same format used by NflApiService.
     *
     * @param date       the calendar date to fetch games for
     * @param dbLeagueId our internal DB league ID — included in returned MatchDtos so
     *                   the frontend can link games back to the correct league
     */
    public List<MatchDto> fetchGameDtosByDate(LocalDate date, Long dbLeagueId) {
        // ESPN scoreboard date format: YYYYMMDD (no dashes)
        String dateStr = date.format(DateTimeFormatter.BASIC_ISO_DATE); // e.g. "20250420"

        EspnScoreboardResponse response = restClient.get()
                .uri("/scoreboard?dates=" + dateStr)
                .retrieve()
                .body(EspnScoreboardResponse.class);

        if (response == null || response.events() == null) return Collections.emptyList();

        return response.events().stream()
                .map(event -> toMatchDto(event, dbLeagueId))
                .toList();
    }

    /**
     * Fetches current NBA standings and converts them to StandingsEntryDtos.
     *
     * Uses a different ESPN host (site.web.api.espn.com) which has richer standings data.
     * Results are sorted globally by win percentage — best record appears first.
     * Returns an empty list gracefully if the endpoint is down or the season is off.
     *
     * @param dbLeagueId our internal DB league ID — included in returned StandingsEntryDtos
     */
    public List<StandingsEntryDto> fetchStandings(Long dbLeagueId) {
        // The NBA season straddles two calendar years (e.g. 2024-25 season starts Oct 2024).
        // ESPN identifies seasons by the year the season ends.
        // Before October → current year hasn't started → use the current year as end year.
        // After October  → use the next year (the season that just started).
        LocalDate today = LocalDate.now();
        int season = today.getMonthValue() >= 10 ? today.getYear() + 1 : today.getYear();

        try {
            // type=1 = overall standings (as opposed to conference-only views)
            EspnStandingsResponse response = standingsClient.get()
                    .uri("/standings?season=" + season + "&type=1")
                    .retrieve()
                    .body(EspnStandingsResponse.class);

            if (response == null || response.children() == null || response.children().isEmpty()) {
                return Collections.emptyList();
            }

            // Build the table conference by conference so East and West stay grouped —
            // the frontend renders them as two separate tables. Within each conference
            // we sort by wins (descending) and assign a within-conference rank of 1–15.
            // (Previously every team was flattened into one win-sorted 1–30 list, which
            // is why the QA sweep saw NBA standings render as a single flat table.)
            List<StandingsEntryDto> result = new ArrayList<>();
            for (EspnConference conference : response.children()) {
                if (conference.standings() == null || conference.standings().entries() == null) continue;

                // A fresh rank counter per conference so each starts again at 1.
                AtomicInteger rank = new AtomicInteger(0);
                conference.standings().entries().stream()
                        .sorted(Comparator.comparingDouble(e -> -getStatValue(e, "wins")))
                        .map(entry -> toStandingsEntryDto(entry, dbLeagueId, rank.incrementAndGet(), conference.name()))
                        .forEach(result::add);
            }

            if (result.isEmpty()) return Collections.emptyList();
            return result;

        } catch (RestClientException e) {
            // Off-season or ESPN structure change — log and return empty list gracefully.
            // The frontend shows "No standings available" rather than crashing.
            log.warn("[NbaApiService] fetchStandings failed for season={}: {}", season, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Fetches an NBA player's full career stats from ESPN.
     *
     * Endpoint: GET /athletes/{espnAthleteId}/stats
     *
     * Returns null if ESPN doesn't recognise the athlete ID (e.g. the player has retired
     * and been removed from active rosters) or if the response is malformed — the caller
     * treats null as "no stats available" and the API returns 204 No Content.
     *
     * Note: this is uncached at the service layer because the controller wraps the result
     * in a long-TTL cache (career stats only change once a day at most).
     *
     * @param espnAthleteId the player's ESPN athlete ID (e.g. "1966" for LeBron James)
     * @return the parsed stats response, or null on any failure
     */
    public PlayerCareerStatsDto fetchCareerStats(String espnAthleteId) {
        if (espnAthleteId == null || espnAthleteId.isBlank()) return null;
        try {
            EspnStatsResponse response = statsClient.get()
                    .uri("/athletes/{id}/stats", espnAthleteId)
                    .retrieve()
                    .body(EspnStatsResponse.class);

            if (response == null || response.categories() == null || response.categories().isEmpty()) {
                return null;
            }
            return toCareerStatsDto(response);

        } catch (RestClientException e) {
            // 404 (unknown athlete), 500 (ESPN hiccup), connection timeout, etc.
            // Log at WARN — visiting a player who has no stats shouldn't fail the page.
            log.warn("[NbaApiService] fetchCareerStats failed for athlete={}: {}", espnAthleteId, e.getMessage());
            return null;
        }
    }

    // ── Private Mapper Methods ────────────────────────────────────────────────

    // Converts one ESPN event (game) into the MatchDto format the frontend expects.
    // Same pattern as NflApiService.toMatchDto — the ESPN scoreboard structure is identical
    // between sports, except NBA teams use a "logo" string instead of "logos" array.
    private MatchDto toMatchDto(EspnEvent event, Long dbLeagueId) {
        // A competition holds the competitors (home + away teams) and their scores.
        // ESPN always wraps one game inside a competitions list — we just take the first.
        EspnCompetition comp = (event.competitions() != null && !event.competitions().isEmpty())
                ? event.competitions().get(0) : null;

        // Find the home and away competitors from the list
        EspnCompetitor homeComp = null;
        EspnCompetitor awayComp = null;
        if (comp != null && comp.competitors() != null) {
            for (EspnCompetitor c : comp.competitors()) {
                if ("home".equals(c.homeAway()))      homeComp = c;
                else if ("away".equals(c.homeAway())) awayComp = c;
            }
        }

        // Build TeamDtos — use the team's logo URL if available
        TeamDto home = homeComp != null ? toTeamDto(homeComp, dbLeagueId) : emptyTeam(dbLeagueId);
        TeamDto away = awayComp != null ? toTeamDto(awayComp, dbLeagueId) : emptyTeam(dbLeagueId);

        // Map ESPN's STATUS_* string to our app's three-state convention
        String statusName = (event.status() != null && event.status().type() != null)
                ? event.status().type().name() : null;
        String mappedStatus = mapStatus(statusName);

        // Only show scores once the game has started — ESPN sends "" for future games
        Integer homeScore = "SCHEDULED".equals(mappedStatus) ? null : parseScore(homeComp);
        Integer awayScore = "SCHEDULED".equals(mappedStatus) ? null : parseScore(awayComp);

        // Convert ESPN's UTC tip-off time to Eastern Time (ET).
        // ZoneId.of("America/New_York") handles EDT/EST transitions automatically —
        // e.g. a 23:30 UTC April game (EDT, UTC-4) becomes 19:30 ET.
        // We strip the timezone after converting so the frontend receives a plain time string
        // (e.g. "19:30:00") that displays correctly as "7:30 PM" in any browser locale.
        LocalDateTime startTime = null;
        if (event.date() != null) {
            try {
                startTime = OffsetDateTime.parse(event.date())
                        .atZoneSameInstant(ZoneId.of("America/New_York"))
                        .toLocalDateTime();
            } catch (Exception ignored) {
                // Malformed date — leave as null
            }
        }

        // ESPN event IDs are strings — parse to Long for MatchDto
        Long matchId = parseId(event.id());

        // Live game clock, e.g. "3RD · 4:12" — only for in-progress games.
        String clock = null;
        if ("LIVE".equals(mappedStatus) && event.status() != null && event.status().displayClock() != null) {
            String pl = nbaPeriodLabel(event.status().period());
            clock = pl.isEmpty() ? event.status().displayClock() : pl + " · " + event.status().displayClock();
        }

        return new MatchDto(matchId, home, away, homeScore, awayScore,
                mappedStatus, startTime, dbLeagueId, "ET", clock);
    }

    // NBA period number → label: 1ST..4TH, then OT / 2OT / 3OT ...
    private static String nbaPeriodLabel(Integer period) {
        if (period == null) return "";
        return switch (period) {
            case 1 -> "1ST";
            case 2 -> "2ND";
            case 3 -> "3RD";
            case 4 -> "4TH";
            case 5 -> "OT";
            default -> (period - 4) + "OT"; // 6 → 2OT, 7 → 3OT
        };
    }

    // Converts an ESPN competitor record to a TeamDto.
    // NBA scoreboard uses a "logo" string field (not "logos" array used in team records).
    private TeamDto toTeamDto(EspnCompetitor competitor, Long dbLeagueId) {
        EspnCompTeam t = competitor.team();
        if (t == null) return emptyTeam(dbLeagueId);

        // "logo" is a single URL string in the NBA scoreboard (unlike the logos[] array in the teams endpoint)
        String crestUrl = t.logo(); // May be null — frontend shows abbreviation fallback

        return new TeamDto(parseId(t.id()), t.displayName(), t.abbreviation(), crestUrl, null, null, dbLeagueId);
    }

    // Returns a blank TeamDto placeholder for cases where competitor data is missing
    private TeamDto emptyTeam(Long dbLeagueId) {
        return new TeamDto(null, "TBD", "TBD", null, null, null, dbLeagueId);
    }

    // Converts one NBA standings entry to a StandingsEntryDto.
    // Basketball has no draws — drawn is always 0.
    // "Points" is set to wins — NBA teams are ranked by win count (not accumulated points).
    private StandingsEntryDto toStandingsEntryDto(EspnStandingsEntry entry, Long dbLeagueId,
                                                  int rank, String conferenceName) {
        EspnStandingsTeam t = entry.team();
        TeamDto team = new TeamDto(
                parseId(t.id()),
                t.displayName(),
                t.abbreviation(),
                // The standings response omits logos, so we derive the crest URL from the
                // team abbreviation (same CDN-derive approach used for player headshots).
                espnTeamLogoUrl(t.abbreviation()),
                null,               // stadium — not in standings response
                t.location(),       // city name
                dbLeagueId);

        int wins   = (int) getStatValue(entry, "wins");
        int losses = (int) getStatValue(entry, "losses");

        return new StandingsEntryDto(
                rank,          // within-conference rank (1–15)
                team,
                wins + losses, // played = wins + losses (no draws in basketball)
                wins,
                0,             // drawn — always 0 in basketball
                losses,
                0,             // goalsFor — not applicable
                0,             // goalsAgainst — not applicable
                wins,          // "points" = wins — ranking metric for basketball
                conferenceName, // "Eastern Conference" / "Western Conference" — drives the grouped layout
                null);         // division — NBA has no division level, so the frontend groups by conference only
    }

    // ESPN serves team logos from a predictable CDN path keyed by the lowercase team
    // abbreviation, e.g. .../nba/500/cle.png for Cleveland. The standings endpoint itself
    // doesn't include logos, so we derive the URL here — the same pattern PlayerService
    // uses to derive headshot URLs from an athlete ID. Returns null if there's no
    // abbreviation to build from (the frontend then shows an abbreviation fallback).
    private String espnTeamLogoUrl(String abbreviation) {
        if (abbreviation == null || abbreviation.isBlank()) return null;
        return "https://a.espncdn.com/i/teamlogos/nba/500/" + abbreviation.toLowerCase() + ".png";
    }

    // Parses the score string from a competitor — ESPN sends empty string "" before the game starts.
    // Returns null instead of 0 so the frontend shows "--" for unstarted games rather than a zero score.
    private Integer parseScore(EspnCompetitor competitor) {
        if (competitor == null || competitor.score() == null || competitor.score().isBlank()) return null;
        try {
            return Integer.parseInt(competitor.score());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    // Parses an ESPN string ID (e.g. "22") to a Long — returns null if parsing fails
    private Long parseId(String id) {
        if (id == null) return null;
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    // Finds a named stat (e.g. "wins") in a standings entry's stats list and returns its value.
    // Returns 0.0 if the stat isn't present — keeps sorting and arithmetic safe.
    private double getStatValue(EspnStandingsEntry entry, String statName) {
        if (entry.stats() == null) return 0.0;
        return entry.stats().stream()
                .filter(s -> statName.equals(s.name()) && s.value() != null)
                .mapToDouble(EspnStat::value)
                .findFirst()
                .orElse(0.0);
    }

    // Maps ESPN's STATUS_* strings to our app's three-state status convention.
    // We use the same states as football-data.org for consistency: FINISHED / LIVE / SCHEDULED.
    private String mapStatus(String espnStatus) {
        if (espnStatus == null) return "SCHEDULED";
        return switch (espnStatus) {
            // Game is over
            case "STATUS_FINAL", "STATUS_FORFEIT" -> "FINISHED";
            // All in-game states — quarters, halftime, overtime, end of period
            case "STATUS_IN_PROGRESS", "STATUS_HALFTIME",
                    "STATUS_END_PERIOD", "STATUS_OVERTIME" -> "LIVE";
            // Everything else (scheduled, pregame, postponed, suspended) = upcoming
            default -> "SCHEDULED";
        };
    }

    // Converts the ESPN stats response into our sport-agnostic PlayerCareerStatsDto.
    // ESPN's structure already matches our DTO shape closely — we mostly rename fields
    // and derive a career SeasonRow from the per-category totals[] array.
    private PlayerCareerStatsDto toCareerStatsDto(EspnStatsResponse response) {
        List<PlayerCareerStatsDto.StatCategory> categories = new ArrayList<>();

        for (EspnStatCategory cat : response.categories()) {
            if (cat.labels() == null || cat.statistics() == null) continue;

            // Per-season rows — chronological order as ESPN returns them.
            // competition is null: NBA only has one competition per row (the NBA itself), so
            // there's nothing to disambiguate — the frontend will hide that column entirely.
            List<PlayerCareerStatsDto.SeasonRow> seasons = cat.statistics().stream()
                    .map(entry -> new PlayerCareerStatsDto.SeasonRow(
                            entry.season() != null ? entry.season().displayName() : null,
                            // ESPN doesn't always populate teamAbbreviation here — fall back to the slug
                            // (which the frontend can display verbatim or look up by name).
                            entry.teamAbbreviation() != null ? entry.teamAbbreviation() : entry.teamSlug(),
                            null,
                            entry.stats() != null ? entry.stats() : Collections.emptyList()))
                    .toList();

            // Career total — ESPN puts it on the parent category, not in the per-season list.
            // Season + team + competition are all null because it spans every team / year.
            PlayerCareerStatsDto.SeasonRow career = (cat.totals() != null && !cat.totals().isEmpty())
                    ? new PlayerCareerStatsDto.SeasonRow(null, null, null, cat.totals())
                    : null;

            categories.add(new PlayerCareerStatsDto.StatCategory(
                    cat.name(),
                    cat.displayName() != null ? cat.displayName() : cat.name(),
                    cat.labels(),
                    seasons,
                    career));
        }

        return new PlayerCareerStatsDto("basketball", categories);
    }

    // ── Box Score ──────────────────────────────────────────────────────────────
    // ESPN's /summary endpoint returns a full game box score for any historical
    // or in-progress NBA game. The event ID we pass here is the same Long that
    // lives in MatchDto.id for NBA games (parsed from ESPN's string event ID in
    // parseId() earlier in this file).
    //
    // URL pattern: /summary?event={eventId}
    // Full URL example: https://site.api.espn.com/apis/site/v2/sports/basketball/nba/summary?event=401705847
    //
    // The response has a "boxscore" object with two arrays:
    //   teams   — one entry per team with aggregate stats (total points, rebounds, etc.)
    //   players — one entry per team with per-player stat tables
    //
    // We return null (not an exception) when the game hasn't started yet or ESPN
    // doesn't have box score data — the controller turns null into HTTP 204.

    public BoxScoreDto fetchBoxScore(Long eventId) {
        try {
            EspnSummaryResponse response = restClient.get()
                    .uri("/summary?event={id}", eventId)
                    .retrieve()
                    .body(EspnSummaryResponse.class);

            if (response == null || response.boxscore() == null) {
                log.debug("[NbaApiService] fetchBoxScore: no boxscore data for event {}", eventId);
                return null;
            }

            EspnBoxscoreData boxscore = response.boxscore();

            // Map the two team stat groups (home + away)
            List<BoxScoreDto.TeamBoxScore> teams = new ArrayList<>();
            if (boxscore.teams() != null) {
                for (EspnTeamStatGroup group : boxscore.teams()) {
                    if (group.team() == null) continue;
                    boolean isHome = "home".equalsIgnoreCase(group.homeAway());
                    List<BoxScoreDto.StatLine> stats = group.statistics() == null
                            ? Collections.emptyList()
                            : group.statistics().stream()
                                    .map(s -> new BoxScoreDto.StatLine(s.label(), s.displayValue()))
                                    .toList();
                    teams.add(new BoxScoreDto.TeamBoxScore(
                            parseId(group.team().id()),
                            group.team().displayName(),
                            group.team().abbreviation(),
                            isHome,
                            stats));
                }
                // Ensure home team is always at index 0
                teams.sort(Comparator.comparing(t -> t.isHome() ? 0 : 1));
            }

            // Map per-player stat tables (one group per team)
            List<BoxScoreDto.PlayerStatGroup> playerStats = new ArrayList<>();
            if (boxscore.players() != null) {
                for (EspnPlayerTeamGroup group : boxscore.players()) {
                    if (group.team() == null || group.statistics() == null || group.statistics().isEmpty()) continue;
                    boolean isHome = "home".equalsIgnoreCase(group.homeAway());

                    // ESPN wraps player stats in a "statistics" array — NBA uses only one
                    // element (index 0) containing the stat table for all players.
                    EspnPlayerStatTable table = group.statistics().get(0);
                    List<String> columns = table.names() != null ? table.names() : Collections.emptyList();

                    List<BoxScoreDto.PlayerStatRow> rows = new ArrayList<>();
                    if (table.athletes() != null) {
                        for (EspnPlayerStatLine line : table.athletes()) {
                            if (line.athlete() == null) continue;
                            // Skip players marked didNotPlay — no stats to show
                            if (Boolean.TRUE.equals(line.didNotPlay())) continue;
                            rows.add(new BoxScoreDto.PlayerStatRow(
                                    line.athlete().displayName(),
                                    parseId(line.athlete().id()),
                                    Boolean.TRUE.equals(line.starter()),  // starter is on the line, not inside athlete
                                    line.stats() != null ? line.stats() : Collections.emptyList()));
                        }
                    }

                    playerStats.add(new BoxScoreDto.PlayerStatGroup(
                            parseId(group.team().id()),
                            group.team().displayName(),
                            isHome,
                            columns,
                            rows));
                }
                // Ensure home team players are at index 0
                playerStats.sort(Comparator.comparing(g -> g.isHome() ? 0 : 1));
            }

            return new BoxScoreDto("basketball", teams, playerStats);

        } catch (RestClientException e) {
            log.warn("[NbaApiService] fetchBoxScore failed for event {}: {}", eventId, e.getMessage());
            return null;
        }
    }

    // ── ESPN Summary inner records ─────────────────────────────────────────────
    // These mirror the JSON structure returned by /summary?event={id}.
    // @JsonIgnoreProperties(ignoreUnknown = true) on every record is essential —
    // the summary response is very large (play-by-play, news, injuries, etc.) and
    // we only care about the "boxscore" section.

    // ── IMPORTANT: These records must be package-private (NOT private). ──────────
    // In Java 21, a `private record`'s canonical constructor is also private,
    // which means Jackson cannot instantiate it during JSON deserialization.
    // Every other ESPN record in this file is already package-private and works
    // correctly. Making these `private` caused body() to silently return null,
    // which made every box score show "unavailable" on the frontend.

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EspnSummaryResponse(EspnBoxscoreData boxscore) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EspnBoxscoreData(
            List<EspnTeamStatGroup> teams,
            List<EspnPlayerTeamGroup> players) {}

    // One entry per team in the teams array — aggregate stats (points, rebounds, etc.)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record EspnTeamStatGroup(
            EspnBoxscoreTeam team,
            String homeAway,                       // "home" or "away"
            List<EspnTeamStat> statistics) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EspnBoxscoreTeam(
            String id,
            String displayName,
            String abbreviation) {}

    // A single aggregate stat for a team (e.g. name="points", label="Points", displayValue="112")
    @JsonIgnoreProperties(ignoreUnknown = true)
    record EspnTeamStat(String name, String label, String displayValue) {}

    // One entry per team in the players array — per-player stat tables
    @JsonIgnoreProperties(ignoreUnknown = true)
    record EspnPlayerTeamGroup(
            EspnBoxscoreTeam team,
            String homeAway,
            List<EspnPlayerStatTable> statistics) {}

    // The stat table for one team: column headers + player rows
    @JsonIgnoreProperties(ignoreUnknown = true)
    record EspnPlayerStatTable(
            List<String> names,                     // column header labels
            List<EspnPlayerStatLine> athletes) {}

    // One player's stat row.
    // IMPORTANT: `starter` lives HERE at the line level, NOT inside EspnAthleteRef.
    // ESPN's JSON looks like: { "athlete": {...}, "stats": [...], "starter": true, "didNotPlay": false }
    // If you put `starter` inside EspnAthleteRef it will always deserialize as null.
    @JsonIgnoreProperties(ignoreUnknown = true)
    record EspnPlayerStatLine(
            EspnAthleteRef athlete,
            List<String> stats,                     // values aligned with table.names
            Boolean starter,                        // true for players in the starting lineup
            Boolean didNotPlay) {}

    // Minimal athlete info needed for the box score row (id + name only — no starter here!)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record EspnAthleteRef(
            String id,
            String displayName) {}
}
