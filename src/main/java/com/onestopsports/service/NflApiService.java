package com.onestopsports.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.onestopsports.dto.MatchDto;
import com.onestopsports.dto.StandingsEntryDto;
import com.onestopsports.dto.TeamDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

// This service talks exclusively to ESPN's unofficial public NFL API.
// Base URL: https://site.api.espn.com/apis/site/v2/sports/football/nfl
//
// Key difference from the other two sports APIs:
// - No API key required — this is a public/unofficial ESPN endpoint
// - Date format for scoreboard: YYYYMMDD (no dashes) e.g. "20250209"
// - Rosters are grouped by side ("offense", "defense", "specialTeam") rather
//   than by position, so we iterate through the groups to find individual players
// - Team/player IDs from ESPN are strings — we parse them to Long for our DB
//
// Note: this is not an officially supported API, so field names or structure
// could change without warning. @JsonIgnoreProperties(ignoreUnknown = true)
// protects us from crashing if ESPN adds or renames fields.
@Service
public class NflApiService {

    private static final Logger log = LoggerFactory.getLogger(NflApiService.class);

    // RestClient for teams, rosters, and scoreboard (site.api.espn.com/apis/site/v2)
    private final RestClient restClient;

    // RestClient for standings — ESPN uses a different URL path (apis/v2 not apis/site/v2)
    private final RestClient standingsClient;

    // NFL divisions are fixed since 2002 — safe to hardcode rather than hit an extra endpoint.
    // Keyed by ESPN team abbreviation (e.g. "NE", "KC", "LAR").
    private static final Map<String, String> DIVISION_BY_ABBR = Map.ofEntries(
            // AFC East
            Map.entry("BUF", "AFC East"),  Map.entry("MIA", "AFC East"),
            Map.entry("NE",  "AFC East"),  Map.entry("NYJ", "AFC East"),
            // AFC North
            Map.entry("BAL", "AFC North"), Map.entry("CIN", "AFC North"),
            Map.entry("CLE", "AFC North"), Map.entry("PIT", "AFC North"),
            // AFC South
            Map.entry("HOU", "AFC South"), Map.entry("IND", "AFC South"),
            Map.entry("JAX", "AFC South"), Map.entry("TEN", "AFC South"),
            // AFC West
            Map.entry("DEN", "AFC West"),  Map.entry("KC",  "AFC West"),
            Map.entry("LV",  "AFC West"),  Map.entry("LAC", "AFC West"),
            // NFC East
            Map.entry("DAL", "NFC East"),  Map.entry("NYG", "NFC East"),
            Map.entry("PHI", "NFC East"),  Map.entry("WSH", "NFC East"),
            // NFC North
            Map.entry("CHI", "NFC North"), Map.entry("DET", "NFC North"),
            Map.entry("GB",  "NFC North"), Map.entry("MIN", "NFC North"),
            // NFC South
            Map.entry("ATL", "NFC South"), Map.entry("CAR", "NFC South"),
            Map.entry("NO",  "NFC South"), Map.entry("TB",  "NFC South"),
            // NFC West
            Map.entry("ARI", "NFC West"),  Map.entry("LAR", "NFC West"),
            Map.entry("SF",  "NFC West"),  Map.entry("SEA", "NFC West")
    );

    // Base URL injected from application.yml — allows overriding in tests
    public NflApiService(
            @Value("${external-api.nfl.base-url}") String baseUrl,
            @Value("${external-api.nfl.standings-url:https://site.api.espn.com/apis/v2/sports/football/nfl}") String standingsUrl) {
        // ESPN's API is public — no auth header needed
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
        // Standings endpoint lives on a different URL path from the rest of the NFL API
        this.standingsClient = RestClient.builder()
                .baseUrl(standingsUrl)
                .build();
    }

    // ── API Response Records ──────────────────────────────────────────────────
    // These inner records mirror ESPN's JSON response structure.
    // The nesting is unusually deep: sports[0].leagues[0].teams[*].team

    @JsonIgnoreProperties(ignoreUnknown = true)
    // Top-level wrapper for GET /teams — contains sports → leagues → teams
    public record EspnTeamsResponse(List<EspnSport> sports) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EspnSport(List<EspnLeague> leagues) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EspnLeague(List<EspnTeamEntry> teams) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    // Each team in the list is wrapped in a { "team": {...} } object
    public record EspnTeamEntry(EspnTeam team) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    // One NFL team — e.g. { "id": "22", "displayName": "Arizona Cardinals", ... }
    public record EspnTeam(
            String id,                    // ESPN's team ID — a string like "22"
            String displayName,           // Full name — e.g. "Arizona Cardinals"
            String abbreviation,          // e.g. "ARI" — shown in score cards
            String location,              // City/state — e.g. "Arizona"
            String name,                  // Short name — e.g. "Cardinals"
            List<EspnLogo> logos) {}      // Team logo URLs — first is the default light-mode logo

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EspnLogo(String href) {} // URL to the logo image

    @JsonIgnoreProperties(ignoreUnknown = true)
    // Response for GET /teams/{id}/roster — players grouped by side of ball
    public record EspnRosterResponse(List<EspnPositionGroup> athletes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    // A group of players on the same side — "offense", "defense", or "specialTeam"
    public record EspnPositionGroup(
            String position,              // Group name: "offense", "defense", or "specialTeam"
            List<EspnAthlete> items) {}   // The actual players in this group

    @JsonIgnoreProperties(ignoreUnknown = true)
    // One NFL player — name, jersey, specific position, and birthplace
    public record EspnAthlete(
            String id,                    // ESPN athlete ID — string like "3139477"
            String fullName,              // e.g. "Patrick Mahomes"
            String jersey,                // e.g. "15" — may be null in the off-season
            EspnAthletePosition position, // The specific position (nested object)
            @JsonProperty("birthPlace") EspnBirthPlace birthPlace) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    // The player's individual position — different from the group (offense/defense/specialTeam)
    public record EspnAthletePosition(
            String name,          // Full position name — e.g. "Quarterback", "Wide Receiver"
            String abbreviation)  // e.g. "QB", "WR"
    {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EspnBirthPlace(
            String city,    // e.g. "Kansas City"
            String state,   // e.g. "MO"
            String country) // e.g. "USA" — closest proxy for nationality we have from ESPN
    {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    // Response for GET /scoreboard — list of games for a date
    public record EspnScoreboardResponse(List<EspnEvent> events) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    // One NFL game event — contains status, both teams, and scores
    public record EspnEvent(
            String id,                        // ESPN event ID — string like "401671759"
            String date,                      // ISO-8601 UTC string — e.g. "2025-09-07T17:00Z"
            EspnEventStatus status,           // Game status (scheduled, in progress, final, etc.)
            List<EspnCompetition> competitions) {} // Always contains exactly one competition

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EspnEventStatus(
            @JsonProperty("type") EspnStatusType type) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EspnStatusType(
            String name,         // Machine-readable: "STATUS_FINAL", "STATUS_IN_PROGRESS", etc.
            String description)  // Human-readable: "Final", "In Progress", "1:00 PM ET"
    {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    // A competition is ESPN's word for one scheduled match — a game between two teams
    public record EspnCompetition(List<EspnCompetitor> competitors) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    // One side in a game — either the home or away team
    public record EspnCompetitor(
            String homeAway,          // "home" or "away"
            EspnCompTeam team,        // The team playing
            String score) {}          // Score as a string (e.g. "40") — empty string before the game starts

    @JsonIgnoreProperties(ignoreUnknown = true)
    // Condensed team info as it appears inside a game/competition
    public record EspnCompTeam(
            String id,
            String displayName,       // e.g. "Philadelphia Eagles"
            String abbreviation,      // e.g. "PHI"
            List<EspnLogo> logos) {}  // May be null — guard before accessing

    // ── Standings response records ─────────────────────────────────────────────
    // The standings endpoint (site.api.espn.com/apis/v2/...) has a DIFFERENT
    // structure from the other NFL endpoints:
    //   top-level → children (conferences) → standings.entries (all 16 teams flat)
    // Divisions are NOT returned by the API — we derive them from the team abbreviation
    // using the hardcoded DIVISION_BY_ABBR map.

    @JsonIgnoreProperties(ignoreUnknown = true)
    // Top-level wrapper — children = [AFC, NFC]
    public record EspnStandingsResponse(List<EspnConference> children) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    // One NFL conference — "American Football Conference" or "National Football Conference".
    // NOTE: unlike what you might expect from the ESPN team/scoreboard API, the standings
    // endpoint does NOT nest divisions inside the conference. Instead it returns all 16
    // conference teams flat in conference.standings.entries. We group into divisions ourselves.
    public record EspnConference(
            String name,                     // "American Football Conference" / "National Football Conference"
            EspnConferenceStandings standings) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    // Wrapper around the flat list of 16 teams in a conference
    public record EspnConferenceStandings(List<EspnStandingsEntry> entries) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    // One row in the standings — a team's season record
    public record EspnStandingsEntry(
            EspnStandingsTeam team,
            List<EspnStat> stats) {}  // Includes "wins", "losses", "ties", "pointsFor", "pointsAgainst", etc.

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EspnStandingsTeam(
            String id,
            String displayName,    // "New England Patriots"
            String abbreviation,   // "NE" — used to look up division from DIVISION_BY_ABBR
            String location,       // "New England"
            List<EspnLogo> logos)  // Logo URLs — reuse EspnLogo from the team/scoreboard records
    {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EspnStat(
            String name,                                     // e.g. "wins", "losses", "ties", "pointsFor"
            @JsonProperty("value") Double value,             // Numeric value
            @JsonProperty("displayValue") String displayValue) {} // Formatted string — e.g. ".824" for win %

    // ── Public API Methods ────────────────────────────────────────────────────

    /**
     * Fetches all 32 NFL teams.
     * ESPN returns all teams in a single response under a deeply nested path:
     * sports[0].leagues[0].teams[*].team
     */
    public EspnTeamsResponse fetchAllTeams() {
        return restClient.get()
                .uri("/teams?limit=32") // Request all 32 teams at once
                .retrieve()
                .body(EspnTeamsResponse.class);
    }

    /**
     * Fetches the roster for a single NFL team by its ESPN team ID.
     * Returns a list of all athletes, flattening the offense/defense/specialTeam groups.
     *
     * @param espnTeamId ESPN's string team ID — e.g. "12" for the Chiefs
     */
    public List<EspnAthlete> fetchPlayersByTeam(String espnTeamId) {
        EspnRosterResponse response = restClient.get()
                .uri("/teams/{id}/roster", espnTeamId)
                .retrieve()
                .body(EspnRosterResponse.class);

        if (response == null || response.athletes() == null) return Collections.emptyList();

        // Flatten all three position groups (offense/defense/specialTeam) into one list
        List<EspnAthlete> allPlayers = new ArrayList<>();
        for (EspnPositionGroup group : response.athletes()) {
            if (group.items() != null) {
                allPlayers.addAll(group.items());
            }
        }
        return allPlayers;
    }

    /**
     * Fetches NFL games on a specific date and converts them to MatchDtos.
     *
     * ESPN's scoreboard uses a different date format than the other APIs:
     * YYYYMMDD with no dashes (e.g. "20250907") rather than "YYYY-MM-DD".
     *
     * @param date       the calendar date to fetch games for
     * @param dbLeagueId our internal DB league ID — included in returned MatchDtos
     */
    public List<MatchDto> fetchGameDtosByDate(LocalDate date, Long dbLeagueId) {
        // ESPN scoreboard takes a date as YYYYMMDD (no dashes)
        String dateStr = date.format(DateTimeFormatter.BASIC_ISO_DATE); // "20250209"

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
     * Fetches current NFL standings grouped by conference and division.
     *
     * ESPN's standings endpoint returns 16 teams flat per conference with NO division grouping.
     * We derive divisions from the team abbreviation using the hardcoded DIVISION_BY_ABBR map
     * (NFL divisions have been stable since 2002 — safe to hardcode).
     *
     * The returned list is ordered: AFC East (1–4), AFC North (1–4), ..., NFC West (1–4).
     * Each entry's division and conference fields tell the frontend how to group them.
     *
     * @param dbLeagueId our internal DB league ID — set on each TeamDto inside the entry
     */
    public List<StandingsEntryDto> fetchStandings(Long dbLeagueId) {
        try {
            // NOTE: standings use a different ESPN URL path (apis/v2, not apis/site/v2)
            // which is why we have a separate standingsClient.
            EspnStandingsResponse response = standingsClient.get()
                    .uri("/standings")
                    .retrieve()
                    .body(EspnStandingsResponse.class);

            if (response == null || response.children() == null || response.children().isEmpty()) {
                return Collections.emptyList();
            }

            // Process each conference (AFC, NFC) and group its 16 teams into 4 divisions.
            // We use a LinkedHashMap to preserve division insertion order (East → North → South → West).
            List<StandingsEntryDto> result = new ArrayList<>();

            for (EspnConference conference : response.children()) {
                if (conference.standings() == null || conference.standings().entries() == null) continue;

                // Group all 16 conference teams by division name.
                // LinkedHashMap preserves insertion order so divisions stay in a consistent sequence.
                Map<String, List<EspnStandingsEntry>> byDivision = new LinkedHashMap<>();
                for (EspnStandingsEntry entry : conference.standings().entries()) {
                    String abbr = entry.team() != null ? entry.team().abbreviation() : null;
                    // Look up division from the hardcoded map; fall back to "Unknown" if unrecognised
                    String divisionName = abbr != null ? DIVISION_BY_ABBR.getOrDefault(abbr, "Unknown") : "Unknown";
                    byDivision.computeIfAbsent(divisionName, k -> new ArrayList<>()).add(entry);
                }

                // For each division: sort teams by wins descending (ties broken by losses ascending),
                // assign within-division rank (1–4), then append to the result list.
                for (Map.Entry<String, List<EspnStandingsEntry>> divEntry : byDivision.entrySet()) {
                    String divisionName = divEntry.getKey();
                    List<EspnStandingsEntry> teams = divEntry.getValue().stream()
                            .sorted(Comparator
                                    .comparingDouble((EspnStandingsEntry e) -> -getStatValue(e, "wins"))
                                    .thenComparingDouble(e ->  getStatValue(e, "losses")))
                            .toList();

                    AtomicInteger divRank = new AtomicInteger(0);
                    for (EspnStandingsEntry entry : teams) {
                        result.add(toStandingsEntryDto(entry, dbLeagueId,
                                divRank.incrementAndGet(), conference.name(), divisionName));
                    }
                }
            }

            return result;

        } catch (RestClientException e) {
            // API structure change or network error — log and return empty gracefully
            log.warn("[NflApiService] fetchStandings failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ── Private Mapper Methods ────────────────────────────────────────────────

    // Converts one ESPN event (game) into the MatchDto format the frontend expects.
    private MatchDto toMatchDto(EspnEvent event, Long dbLeagueId) {
        // A competition holds the competitors (home + away teams) and their scores.
        // ESPN always wraps a single game inside a competitions list — we just take the first.
        EspnCompetition comp = (event.competitions() != null && !event.competitions().isEmpty())
                ? event.competitions().get(0) : null;

        // Find the home and away competitor from the competitions list
        EspnCompetitor homeComp = null;
        EspnCompetitor awayComp = null;
        if (comp != null && comp.competitors() != null) {
            for (EspnCompetitor c : comp.competitors()) {
                if ("home".equals(c.homeAway())) homeComp = c;
                else if ("away".equals(c.homeAway())) awayComp = c;
            }
        }

        // Build TeamDtos from the competitor data — use abbreviation fallback if no crest
        TeamDto home = homeComp != null ? toTeamDto(homeComp, dbLeagueId) : emptyTeam(dbLeagueId);
        TeamDto away = awayComp != null ? toTeamDto(awayComp, dbLeagueId) : emptyTeam(dbLeagueId);

        // Map the ESPN status string to our app's status convention
        String statusName = (event.status() != null && event.status().type() != null)
                ? event.status().type().name() : null;
        String mappedStatus = mapStatus(statusName);

        // Only show scores once the game has started — ESPN sends "0" for scheduled games
        Integer homeScore = "SCHEDULED".equals(mappedStatus) ? null : parseScore(homeComp);
        Integer awayScore = "SCHEDULED".equals(mappedStatus) ? null : parseScore(awayComp);

        // Convert ESPN's UTC kick-off time to Eastern Time (ET).
        // ZoneId.of("America/New_York") handles EDT/EST transitions automatically —
        // e.g. a 1:00 PM ET Sunday game (18:00 UTC in EDT) stays as 13:00 ET.
        // We strip the timezone after converting so the frontend receives a plain time string
        // (e.g. "13:00:00") that displays correctly as "1:00 PM" in any browser locale.
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

        // ESPN event IDs are strings — parse to Long for our MatchDto
        Long matchId = null;
        try {
            if (event.id() != null) matchId = Long.parseLong(event.id());
        } catch (NumberFormatException ignored) {}

        return new MatchDto(matchId, home, away, homeScore, awayScore,
                mappedStatus, startTime, dbLeagueId, "ET");
    }

    // Converts an ESPN competitor record to a TeamDto.
    private TeamDto toTeamDto(EspnCompetitor competitor, Long dbLeagueId) {
        EspnCompTeam t = competitor.team();
        if (t == null) return emptyTeam(dbLeagueId);

        // Use the first logo in the array as the crest URL (it's the default light-mode one)
        String crestUrl = (t.logos() != null && !t.logos().isEmpty()) ? t.logos().get(0).href() : null;

        Long teamId = null;
        try {
            if (t.id() != null) teamId = Long.parseLong(t.id());
        } catch (NumberFormatException ignored) {}

        return new TeamDto(teamId, t.displayName(), t.abbreviation(), crestUrl, null, null, dbLeagueId);
    }

    // Returns a blank TeamDto placeholder for cases where competitor data is missing
    private TeamDto emptyTeam(Long dbLeagueId) {
        return new TeamDto(null, "TBD", "TBD", null, null, null, dbLeagueId);
    }

    // Converts one standings entry to a StandingsEntryDto.
    // NFL doesn't use traditional points (3W+1D) — wins is the primary ranking metric.
    // We reuse goalsFor/goalsAgainst for points-for/points-against so the frontend
    // can show PF, PA, and DIFF (goalsFor − goalsAgainst) in the NFL-specific table layout.
    private StandingsEntryDto toStandingsEntryDto(EspnStandingsEntry entry, Long dbLeagueId,
                                                   int rank, String conference, String division) {
        EspnStandingsTeam t = entry.team();

        // Grab crest URL from the logo list if ESPN includes it in the standings response
        String crestUrl = (t.logos() != null && !t.logos().isEmpty()) ? t.logos().get(0).href() : null;

        TeamDto team = new TeamDto(
                parseId(t.id()),
                t.displayName(),
                t.abbreviation(),
                crestUrl,
                null,          // stadium — not in standings data
                t.location(),  // city — used as "country" field in TeamDto
                dbLeagueId);

        int wins        = (int) getStatValue(entry, "wins");
        int losses      = (int) getStatValue(entry, "losses");
        int ties        = (int) getStatValue(entry, "ties");       // rare but real in NFL
        int played      = wins + losses + ties;
        int pointsFor   = (int) getStatValue(entry, "pointsFor");
        int pointsAgainst = (int) getStatValue(entry, "pointsAgainst");

        return new StandingsEntryDto(
                rank,           // within-division rank (1–4)
                team,
                played,
                wins,
                ties,           // drawn = ties (NFL allows ties, e.g. 2022 Bengals–Eagles)
                losses,
                pointsFor,      // goalsFor reused for NFL points scored
                pointsAgainst,  // goalsAgainst reused for NFL points allowed
                wins,           // points = wins — no traditional points system in NFL
                conference,     // "American Football Conference" or "National Football Conference"
                division);      // "AFC East", "NFC West", etc.
    }

    // Parses the score string from a competitor — ESPN sends empty string "" before the game starts.
    // Returns null for empty/null score strings rather than 0 so the frontend shows "--" not "0".
    private Integer parseScore(EspnCompetitor competitor) {
        if (competitor == null || competitor.score() == null || competitor.score().isBlank()) return null;
        try {
            return Integer.parseInt(competitor.score());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    // Parses an ESPN string ID ("22") into a Long — returns null if parsing fails
    private Long parseId(String id) {
        if (id == null) return null;
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    // Finds a named stat (e.g. "wins") in an entry's stats list and returns its numeric value.
    // Returns 0.0 if the stat isn't present so sorting and arithmetic stay safe.
    private double getStatValue(EspnStandingsEntry entry, String statName) {
        if (entry.stats() == null) return 0.0;
        return entry.stats().stream()
                .filter(s -> statName.equals(s.name()) && s.value() != null)
                .mapToDouble(EspnStat::value)
                .findFirst()
                .orElse(0.0);
    }

    // Maps ESPN's STATUS_* strings to our app's status conventions.
    // Our app uses the same set as football-data.org: FINISHED / LIVE / SCHEDULED.
    private String mapStatus(String espnStatus) {
        if (espnStatus == null) return "SCHEDULED";
        return switch (espnStatus) {
            case "STATUS_FINAL", "STATUS_FORFEIT" -> "FINISHED";
            // All in-game states — quarters, overtime, two-minute warning, etc. — map to LIVE
            case "STATUS_IN_PROGRESS", "STATUS_HALFTIME",
                    "STATUS_END_PERIOD", "STATUS_OVERTIME" -> "LIVE";
            // Everything else (scheduled, pregame, delayed, postponed) is treated as upcoming
            default -> "SCHEDULED";
        };
    }
}
