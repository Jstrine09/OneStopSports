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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
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

    // The pre-configured HTTP client — base URL baked in at startup
    private final RestClient restClient;

    // Base URL injected from application.yml — allows overriding in tests
    public NflApiService(@Value("${external-api.nfl.base-url}") String baseUrl) {
        // ESPN's API is public — no auth header needed
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    // Response for GET /standings?season=YYYY&seasontype=2
    // Conferences (AFC, NFC) → Divisions → Teams with win/loss stats
    public record EspnStandingsResponse(List<EspnConference> children) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    // One NFL conference — "AFC" or "NFC"
    public record EspnConference(
            String name,
            List<EspnDivision> children) {} // Four divisions per conference

    @JsonIgnoreProperties(ignoreUnknown = true)
    // One division — e.g. "AFC East", "NFC West"
    public record EspnDivision(
            String name,
            EspnStandingsGroup standings) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EspnStandingsGroup(List<EspnStandingsEntry> entries) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    // One row in the standings — a team's season record
    public record EspnStandingsEntry(
            EspnStandingsTeam team,
            List<EspnStat> stats) {}  // Includes "wins", "losses", "ties", "rank", etc.

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EspnStandingsTeam(
            String id,
            String displayName,
            String abbreviation,
            String location) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EspnStat(
            String name,                                    // e.g. "wins", "losses", "rank"
            @JsonProperty("value") Double value,            // Numeric value
            @JsonProperty("displayValue") String displayValue) {} // Formatted string

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
     * Fetches current NFL standings and converts them to StandingsEntryDtos.
     *
     * Uses the most recent completed regular season (seasontype=2).
     * Returns an empty list during the off-season — the standings endpoint returns
     * no data when there is no active season (same behaviour as NBA balldontlie).
     *
     * @param dbLeagueId our internal DB league ID — included in returned MatchDtos
     */
    public List<StandingsEntryDto> fetchStandings(Long dbLeagueId) {
        // Use the year of the most recently completed regular season.
        // NFL seasons straddle two years: the 2024 season = Sep 2024 → Feb 2025.
        // Before September, the most recent completed season used the previous year.
        LocalDate today = LocalDate.now();
        int season = today.getMonthValue() < 9 ? today.getYear() - 1 : today.getYear();

        try {
            // seasontype=2 = regular season standings (1 = preseason, 3 = postseason)
            EspnStandingsResponse response = restClient.get()
                    .uri("/standings?season=" + season + "&seasontype=2")
                    .retrieve()
                    .body(EspnStandingsResponse.class);

            if (response == null || response.children() == null || response.children().isEmpty()) {
                return Collections.emptyList();
            }

            // Collect all entries from all conferences and all divisions into one flat list
            List<EspnStandingsEntry> allEntries = new ArrayList<>();
            for (EspnConference conference : response.children()) {
                if (conference.children() == null) continue;
                for (EspnDivision division : conference.children()) {
                    if (division.standings() == null || division.standings().entries() == null) continue;
                    allEntries.addAll(division.standings().entries());
                }
            }

            if (allEntries.isEmpty()) return Collections.emptyList();

            // Build a list of StandingsEntryDtos and sort by overall rank (wins descending)
            List<StandingsEntryDto> result = new ArrayList<>();
            AtomicInteger rank = new AtomicInteger(0); // We'll assign rank after sorting

            List<EspnStandingsEntry> sorted = allEntries.stream()
                    .sorted(Comparator.comparingDouble(e -> -getStatValue(e, "wins")))
                    .toList();

            for (EspnStandingsEntry entry : sorted) {
                result.add(toStandingsEntryDto(entry, dbLeagueId, rank.incrementAndGet()));
            }

            return result;

        } catch (RestClientException e) {
            // Off-season or API structure change — log and return empty gracefully
            log.warn("[NflApiService] fetchStandings failed for season={}: {}", season, e.getMessage());
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

        // Parse the ISO-8601 UTC date string into a LocalDateTime for the frontend kick-off display
        LocalDateTime startTime = null;
        if (event.date() != null) {
            try {
                startTime = OffsetDateTime.parse(event.date()).toLocalDateTime();
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
                mappedStatus, startTime, dbLeagueId);
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
    // NFL doesn't have draws — drawn is always 0.
    private StandingsEntryDto toStandingsEntryDto(EspnStandingsEntry entry, Long dbLeagueId, int rank) {
        EspnStandingsTeam t = entry.team();
        TeamDto team = new TeamDto(
                parseId(t.id()),
                t.displayName(),
                t.abbreviation(),
                null, null,
                t.location(),
                dbLeagueId);

        int wins   = (int) getStatValue(entry, "wins");
        int losses = (int) getStatValue(entry, "losses");
        int ties   = (int) getStatValue(entry, "ties");   // NFL allows ties (rare but real)
        int played = wins + losses + ties;

        return new StandingsEntryDto(
                rank,
                team,
                played,
                wins,
                ties,    // NFL ties ≈ "drawn" — same concept
                losses,
                0,       // goalsFor — not applicable to NFL (points exist but aren't tracked here)
                0,       // goalsAgainst — same
                wins);   // "points" = wins — NFL ranks by win percentage, wins is the closest proxy
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
