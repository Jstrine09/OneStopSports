package com.onestopsports.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.onestopsports.dto.MatchDto;
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

    // Both base URLs are injected from application.yml so they can be overridden in tests.
    // No API key is needed — ESPN's unofficial API is publicly accessible.
    public NbaApiService(
            @Value("${external-api.nba.base-url}") String baseUrl,
            @Value("${external-api.nba.standings-url}") String standingsUrl) {
        // No Authorization header needed — ESPN API is publicly accessible
        this.restClient       = RestClient.builder().baseUrl(baseUrl).build();
        this.standingsClient  = RestClient.builder().baseUrl(standingsUrl).build();
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
    public record EspnEventStatus(EspnStatusType type) {}

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

            // Collect all entries from both conferences into one flat list
            List<EspnStandingsEntry> allEntries = new ArrayList<>();
            for (EspnConference conference : response.children()) {
                if (conference.standings() == null || conference.standings().entries() == null) continue;
                allEntries.addAll(conference.standings().entries());
            }

            if (allEntries.isEmpty()) return Collections.emptyList();

            // Sort all 30 teams by wins (descending), then assign rank 1–30
            AtomicInteger rank = new AtomicInteger(0);
            return allEntries.stream()
                    .sorted(Comparator.comparingDouble(e -> -getStatValue(e, "wins")))
                    .map(entry -> toStandingsEntryDto(entry, dbLeagueId, rank.incrementAndGet()))
                    .toList();

        } catch (RestClientException e) {
            // Off-season or ESPN structure change — log and return empty list gracefully.
            // The frontend shows "No standings available" rather than crashing.
            log.warn("[NbaApiService] fetchStandings failed for season={}: {}", season, e.getMessage());
            return Collections.emptyList();
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

        // Parse the ISO-8601 UTC date string into a LocalDateTime for the tip-off time display
        LocalDateTime startTime = null;
        if (event.date() != null) {
            try {
                startTime = OffsetDateTime.parse(event.date()).toLocalDateTime();
            } catch (Exception ignored) {
                // Malformed date — leave as null
            }
        }

        // ESPN event IDs are strings — parse to Long for MatchDto
        Long matchId = parseId(event.id());

        return new MatchDto(matchId, home, away, homeScore, awayScore,
                mappedStatus, startTime, dbLeagueId);
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
    private StandingsEntryDto toStandingsEntryDto(EspnStandingsEntry entry, Long dbLeagueId, int rank) {
        EspnStandingsTeam t = entry.team();
        TeamDto team = new TeamDto(
                parseId(t.id()),
                t.displayName(),
                t.abbreviation(),
                null, null,         // no crest/stadium in standings response
                t.location(),       // city name
                dbLeagueId);

        int wins   = (int) getStatValue(entry, "wins");
        int losses = (int) getStatValue(entry, "losses");

        return new StandingsEntryDto(
                rank,          // global rank across both conferences (1–30)
                team,
                wins + losses, // played = wins + losses (no draws in basketball)
                wins,
                0,             // drawn — always 0 in basketball
                losses,
                0,             // goalsFor — not applicable
                0,             // goalsAgainst — not applicable
                wins);         // "points" = wins — ranking metric for basketball
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
}
