package com.onestopsports.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.onestopsports.dto.MatchDto;
import com.onestopsports.dto.StandingsEntryDto;
import com.onestopsports.dto.TeamDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

// This service is the NBA equivalent of ExternalApiService.
// It talks exclusively to balldontlie.io (https://api.balldontlie.io/v1),
// the free NBA API that gives us teams, rosters, game scores, and standings.
//
// Key difference from the football API:
// - Auth: Bearer token in the Authorization header (not X-Auth-Token)
// - URLs use array-style parameters: ?dates[]=YYYY-MM-DD, ?team_ids[]=123
// - Pagination: cursor-based (meta.next_cursor) instead of page numbers
@Service
public class NbaApiService {

    // The pre-configured HTTP client — has the balldontlie base URL and Bearer token baked in
    private final RestClient restClient;

    // Values injected from application-local.yml (gitignored for security)
    public NbaApiService(
            @Value("${external-api.nba.base-url}") String baseUrl,
            @Value("${external-api.nba.api-key}") String apiKey) {

        // Build the RestClient once at startup.
        // "Authorization: Bearer <key>" is the auth format balldontlie expects.
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    // ── API Response Records ──────────────────────────────────────────────────
    // These inner records mirror the JSON structure balldontlie.io returns.
    // @JsonIgnoreProperties(ignoreUnknown = true) means extra fields in the API
    // response won't crash the app — we just ignore what we don't need.
    // @JsonProperty is needed wherever the JSON field name uses snake_case
    // but we want a camelCase Java field name (e.g. "full_name" → fullName).

    @JsonIgnoreProperties(ignoreUnknown = true)
    // Response wrapper for GET /teams — contains the list of all 30 NBA teams
    public record NbaTeamsResponse(List<NbaTeam> data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    // One NBA team — e.g. { "id": 1, "name": "Celtics", "full_name": "Boston Celtics", ... }
    public record NbaTeam(
            Long id,
            String name,                                  // Short name — e.g. "Celtics"
            @JsonProperty("full_name") String fullName,   // Full name — e.g. "Boston Celtics"
            String abbreviation,                          // e.g. "BOS" — used as short name in the app
            String city,                                  // e.g. "Boston"
            String conference,                            // "East" or "West"
            String division) {}                           // e.g. "Atlantic"

    @JsonIgnoreProperties(ignoreUnknown = true)
    // Response wrapper for GET /players — paginated, so it also includes metadata
    public record NbaPlayersResponse(List<NbaPlayer> data, NbaMeta meta) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    // Pagination metadata — next_cursor is null when there are no more pages
    public record NbaMeta(@JsonProperty("next_cursor") Integer nextCursor) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    // One NBA player — e.g. { "id": 237, "first_name": "LeBron", "last_name": "James", "position": "F", ... }
    public record NbaPlayer(
            Long id,
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName,
            String position,                                    // "G", "F", "C", "G-F", or "F-C"
            @JsonProperty("jersey_number") String jerseyNumber, // String — e.g. "23" (sometimes null)
            String country,                                     // Nationality — e.g. "USA"
            NbaTeam team) {}                                    // May be null if player is a free agent

    @JsonIgnoreProperties(ignoreUnknown = true)
    // Response wrapper for GET /games — the list of games on a given date
    public record NbaGamesResponse(List<NbaGame> data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    // One NBA game — includes score, status, and both teams
    public record NbaGame(
            Long id,
            String date,                                                    // "YYYY-MM-DD"
            String status,                                                  // "Final", "In Progress", "7:30 pm ET", etc.
            @JsonProperty("home_team") NbaTeam homeTeam,
            @JsonProperty("visitor_team") NbaTeam visitorTeam,
            @JsonProperty("home_team_score") Integer homeTeamScore,         // 0 for future games
            @JsonProperty("visitor_team_score") Integer visitorTeamScore) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    // Response wrapper for GET /standings
    public record NbaStandingsResponse(List<NbaStandingEntry> data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    // One row in the NBA standings — one team's season record
    public record NbaStandingEntry(
            NbaTeam team,
            Integer wins,
            Integer losses,
            String conference,
            @JsonProperty("conference_rank") Integer conferenceRank,
            @JsonProperty("overall_rank") Integer overallRank) {}

    // ── Public API Methods ────────────────────────────────────────────────────

    /**
     * Fetches all 30 NBA teams.
     * balldontlie returns all teams in a single response — no pagination needed here.
     * Used by NbaDataLoader at startup to seed the database.
     */
    public NbaTeamsResponse fetchAllTeams() {
        return restClient.get()
                .uri("/teams?per_page=100") // 30 teams fits in one page
                .retrieve()
                .body(NbaTeamsResponse.class);
    }

    /**
     * Fetches all players for a specific NBA team using cursor pagination.
     *
     * balldontlie uses cursor-based pagination — each response includes a
     * meta.next_cursor value. We keep requesting new pages until next_cursor is null.
     *
     * teamId here is balldontlie's team ID (not our DB team ID).
     */
    public List<NbaPlayer> fetchPlayersByTeam(Long teamId) {
        List<NbaPlayer> allPlayers = new ArrayList<>();
        Integer cursor = null; // null = first page (no cursor yet)

        do {
            // Build the URL — ?team_ids[]=<id> is balldontlie's array parameter format
            String uri = "/players?team_ids[]=" + teamId + "&per_page=100"
                    + (cursor != null ? "&cursor=" + cursor : "");

            NbaPlayersResponse page = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(NbaPlayersResponse.class);

            if (page == null || page.data() == null) break;

            allPlayers.addAll(page.data());

            // Get the cursor for the next page — null means we're on the last page
            cursor = (page.meta() != null) ? page.meta().nextCursor() : null;

            // If there are more pages, sleep before the next request.
            // balldontlie returns historical players — franchises like the Lakers
            // have 500+ all-time players across 5+ pages. Without this sleep,
            // rapid-fire page requests trigger a 429 even with a slow outer loop.
            if (cursor != null) {
                try {
                    Thread.sleep(2_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Restore the interrupted status
                    break;
                }
            }

        } while (cursor != null); // Keep looping until there are no more pages

        return allPlayers;
    }

    /**
     * Fetches NBA games on a specific date and converts them to MatchDtos.
     *
     * dbLeagueId is our internal database ID for the NBA league — we pass it in
     * so the returned MatchDtos can link back to the correct league in the frontend.
     */
    public List<MatchDto> fetchGameDtosByDate(LocalDate date, Long dbLeagueId) {
        // balldontlie array parameter format: ?dates[]=YYYY-MM-DD
        String uri = "/games?dates[]=" + date;

        NbaGamesResponse response = restClient.get()
                .uri(uri)
                .retrieve()
                .body(NbaGamesResponse.class);

        if (response == null || response.data() == null) return Collections.emptyList();

        return response.data().stream()
                .map(g -> toMatchDto(g, dbLeagueId))
                .toList();
    }

    /**
     * Fetches the current NBA standings and converts them to StandingsEntryDtos.
     *
     * The NBA season straddles two calendar years (e.g. 2024-25 season starts in October 2024).
     * balldontlie refers to seasons by their start year — so the 2024-25 season = season 2024.
     * If we're before October, the current season hasn't started yet, so we use last year.
     */
    public List<StandingsEntryDto> fetchStandings(Long dbLeagueId) {
        // Determine which season year to use
        LocalDate today = LocalDate.now();
        int season = today.getMonthValue() < 10 ? today.getYear() - 1 : today.getYear();

        NbaStandingsResponse response = restClient.get()
                .uri("/standings?season=" + season)
                .retrieve()
                .body(NbaStandingsResponse.class);

        if (response == null || response.data() == null) return Collections.emptyList();

        // Sort by overall league rank before returning
        return response.data().stream()
                .sorted(Comparator.comparingInt(e -> e.overallRank() != null ? e.overallRank() : 999))
                .map(e -> toStandingsEntryDto(e, dbLeagueId))
                .toList();
    }

    // ── Private Mapper Methods ────────────────────────────────────────────────

    // Converts a raw NBA game into the MatchDto format the frontend expects.
    // We reuse the same MatchDto as football — status strings are mapped to our app's conventions.
    private MatchDto toMatchDto(NbaGame g, Long dbLeagueId) {
        // Build minimal TeamDtos — NBA teams don't have crests or stadiums from this endpoint
        TeamDto home = new TeamDto(
                g.homeTeam().id(),           // balldontlie team ID (not our DB ID)
                g.homeTeam().fullName(),      // e.g. "Boston Celtics"
                g.homeTeam().abbreviation(),  // e.g. "BOS" — shown in score cards
                null,                         // no crest URL (not in free tier)
                null,                         // no stadium
                g.homeTeam().city(),          // e.g. "Boston"
                dbLeagueId);

        TeamDto away = new TeamDto(
                g.visitorTeam().id(),
                g.visitorTeam().fullName(),
                g.visitorTeam().abbreviation(),
                null,
                null,
                g.visitorTeam().city(),
                dbLeagueId);

        // balldontlie returns scores as 0 for future games — treat 0 scores in SCHEDULED games as null
        String mappedStatus = mapStatus(g.status());
        Integer homeScore = "SCHEDULED".equals(mappedStatus) ? null : g.homeTeamScore();
        Integer awayScore  = "SCHEDULED".equals(mappedStatus) ? null : g.visitorTeamScore();

        // Convert date string "YYYY-MM-DD" to LocalDateTime (midnight = start of that day)
        LocalDateTime startTime = null;
        if (g.date() != null) {
            try {
                startTime = LocalDate.parse(g.date()).atStartOfDay();
            } catch (Exception ignored) {
                // Malformed date — leave startTime as null
            }
        }

        return new MatchDto(g.id(), home, away, homeScore, awayScore,
                mappedStatus, startTime, dbLeagueId);
    }

    // Converts one row of NBA standings data into the StandingsEntryDto the frontend uses.
    // Basketball doesn't have draws, goalsFor, or goalsAgainst — those fields are set to 0.
    // "Points" is mapped to wins — in basketball, teams are ranked by win-loss record, not points.
    private StandingsEntryDto toStandingsEntryDto(NbaStandingEntry e, Long dbLeagueId) {
        TeamDto team = new TeamDto(
                e.team().id(),
                e.team().fullName(),
                e.team().abbreviation(),
                null, null,
                e.team().city(),
                dbLeagueId);

        int wins   = e.wins()   != null ? e.wins()   : 0;
        int losses = e.losses() != null ? e.losses() : 0;

        return new StandingsEntryDto(
                e.overallRank(),   // position in the overall league standings
                team,
                wins + losses,     // played = wins + losses (no draws in basketball)
                wins,              // won
                0,                 // drawn — always 0 in basketball
                losses,            // lost
                0,                 // goalsFor — not applicable to basketball
                0,                 // goalsAgainst — not applicable to basketball
                wins);             // points — use wins as the ranking metric
    }

    // Maps balldontlie's game status strings to our app's status conventions.
    // football-data.org uses "FINISHED" / "LIVE" / "TIMED" — we keep that standard here.
    private String mapStatus(String nbaStatus) {
        if (nbaStatus == null) return "SCHEDULED";
        String lower = nbaStatus.toLowerCase();
        if (lower.equals("final"))        return "FINISHED";
        if (lower.contains("progress"))   return "LIVE";
        if (lower.contains("qtr") || lower.contains("half") || lower.contains("ot")) return "LIVE";
        return "SCHEDULED"; // Future games (show tip-off time like "7:30 pm ET")
    }
}
