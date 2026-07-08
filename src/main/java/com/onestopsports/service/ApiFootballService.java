package com.onestopsports.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.onestopsports.dto.PlayerCareerStatsDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Talks to api-sports.io's football API (https://v3.football.api-sports.io).
//
// We use this for one thing only: per-season stats on individual football (soccer) players.
// football-data.org (our main football source) doesn't expose player stats on its free tier,
// so this service plugs that gap.
//
// Two-step flow because the free tier requires a league filter on player search:
//   1. searchPlayerId(name, leagueId, season) → finds the API-SPORTS player ID by name match
//   2. fetchPlayerStats(playerId, season)     → fetches the actual season stats
//
// The caller (PlayerService) caches the result of step 1 by writing the player ID onto
// Player.externalId, so step 1 only runs once per player. Subsequent visits skip straight
// to step 2.
//
// Rate limit (free tier): 100 requests per DAY. This is tight — production use would need
// server-side caching (Redis @Cacheable with a 24h+ TTL). We currently rely on:
//   - Player.externalId persistence to avoid re-searching
//   - React Query's 24h staleTime on the frontend to deduplicate within a session
// If we hit the limit, the API returns 429 and we log + return null (the frontend hides
// the stats card).
//
// API response shape (one season at a time):
//   { "response": [ { "player": {...}, "statistics": [{ "team": {...}, "games": {...},
//                     "goals": {...}, "passes": {...}, "cards": {...}, ... }] } ] }
@Service
public class ApiFootballService {

    private static final Logger log = LoggerFactory.getLogger(ApiFootballService.class);

    // Map our DB league.external_id (football-data.org competition IDs) → API-SPORTS league ID.
    // These IDs are stable — API-SPORTS has had the same league IDs since at least 2018.
    // Source: https://dashboard.api-football.com/soccer/ids
    private static final Map<Integer, Integer> FOOTBALL_DATA_TO_API_SPORTS_LEAGUE = Map.of(
            2021, 39,   // Premier League
            2014, 140,  // La Liga (Primera Division)
            2002, 78,   // Bundesliga
            2019, 135,  // Serie A
            2015, 61,   // Ligue 1
            2001, 2     // UEFA Champions League
    );

    private final RestClient restClient;

    // @Autowired is required here because we also have a package-private test constructor below —
    // Spring needs to know which constructor to use for production dependency injection.
    @org.springframework.beans.factory.annotation.Autowired
    public ApiFootballService(
            @Value("${external-api.api-football.base-url}") String baseUrl,
            @Value("${external-api.api-football.api-key}") String apiKey) {
        // api-sports.io direct uses the x-apisports-key header.
        // (If you signed up via RapidAPI, swap base-url + use x-rapidapi-key + x-rapidapi-host.)
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("x-apisports-key", apiKey)
                .build();
    }

    // Package-private test constructor — accepts a pre-built RestClient instance.
    // Used by ApiFootballServiceTest so we can inject a mock client without starting a real HTTP server.
    // Never called by Spring — only by unit tests in the same package.
    ApiFootballService(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Returns the API-SPORTS league ID for one of our DB leagues, or null if we don't have
     * a mapping (e.g. a domestic cup competition we haven't added).
     *
     * Exposed package-private so PlayerService can decide whether a football player has
     * a chance of returning stats before firing the search request.
     *
     * @param footballDataLeagueId the value of League.externalId (a football-data.org ID)
     */
    Integer mapLeagueId(Integer footballDataLeagueId) {
        if (footballDataLeagueId == null) return null;
        return FOOTBALL_DATA_TO_API_SPORTS_LEAGUE.get(footballDataLeagueId);
    }

    // Free-tier cap on the `season` parameter. API-SPORTS' free plan only serves
    // seasons 2022, 2023, and 2024 — any higher year returns:
    //   { "errors": { "plan": "Free plans do not have access to this season, ..." } }
    // Bump this (or remove the cap entirely) if/when the account is upgraded.
    private static final int FREE_TIER_MAX_SEASON = 2024;

    /**
     * Returns the European football season we should query API-SPORTS for, as their API
     * represents it (the year the season STARTS — so the 2024-25 season = 2024).
     *
     * Naively this would be: month ≥ 7 → year; otherwise → year - 1. But the free tier
     * caps out at 2024, so we clamp to FREE_TIER_MAX_SEASON. As of mid-2026 this means
     * we serve the 2024-25 season (the most recent fully-played season available to us)
     * rather than the in-progress 2025-26 season.
     */
    int currentSeason() {
        LocalDate today = LocalDate.now();
        int naturalSeason = today.getMonthValue() >= 7 ? today.getYear() : today.getYear() - 1;
        return Math.min(naturalSeason, FREE_TIER_MAX_SEASON);
    }

    /**
     * Searches API-SPORTS for a player by name within a specific league + season.
     *
     * The free tier requires a league or team filter on search — we use league because
     * we have it via the DB chain (player → team → league.externalId → mapLeagueId).
     *
     * The search param matches against API-SPORTS' internal name index. To find an exact
     * match across "Erling Haaland" / "E. Haaland" / "Erling Braut Haaland", we filter
     * results in code by last-name match (case-insensitive). This also handles transfers:
     * if a player moved leagues we'd miss them, but persisting the externalId means the
     * next stats request goes directly to fetchPlayerStats and skips the search entirely.
     *
     * @return the API-SPORTS player ID, or empty if no clear match
     */
    public Optional<Integer> searchPlayerId(String fullName, int apiSportsLeagueId, int season) {
        if (fullName == null || fullName.isBlank()) return Optional.empty();

        // API-SPORTS' search param requires ≥ 4 characters AND rejects any non-alphanumeric
        // character — including accented Latin letters like é, ñ, ø. We strip accents from
        // the search term ("Dembélé" → "Dembele") so European names with diacritics work.
        // Name matching against the response is still done against the original (accented)
        // string, since the API returns names with their accents intact in the response body.
        String[] parts = fullName.trim().split("\\s+");
        String lastName  = stripAccents(parts[parts.length - 1]);
        String firstName = stripAccents(parts[0]);
        final String searchTerm =
                lastName.length() >= 4 ? lastName
                : firstName.length() >= 4 ? firstName
                : null;
        if (searchTerm == null) {
            log.debug("[ApiFootballService] Name '{}' too short to search — skipping.", fullName);
            return Optional.empty();
        }

        try {
            ApiResponse response = restClient.get()
                    .uri(uri -> uri.path("/players")
                            .queryParam("league", apiSportsLeagueId)
                            .queryParam("season", season)
                            .queryParam("search", searchTerm)
                            .build())
                    .retrieve()
                    .body(ApiResponse.class);

            if (response == null || response.response() == null || response.response().isEmpty()) {
                log.debug("[ApiFootballService] No match for '{}' in league={} season={}",
                        fullName, apiSportsLeagueId, season);
                return Optional.empty();
            }

            // Find the entry whose full name best matches our query. Both sides are
            // accent-stripped + lowercased so "Vinícius" (DB) matches "Vinicius" (API)
            // and vice-versa. We prefer an exact match on the concatenated
            // "firstname lastname"; fall back to any entry whose lastname matches;
            // finally take the first result.
            String normalisedQuery = stripAccents(fullName.trim()).toLowerCase();
            for (ApiPlayerEntry entry : response.response()) {
                if (entry.player() == null) continue;
                String candidate = stripAccents(
                        (safe(entry.player().firstname()) + " " + safe(entry.player().lastname())).trim()
                ).toLowerCase();
                if (candidate.equals(normalisedQuery)) {
                    return Optional.of(entry.player().id());
                }
            }
            // Loose fallback: lastname match. Helpful when DB has "L. Messi" / API has full name.
            String lastNameLc = stripAccents(parts[parts.length - 1]).toLowerCase();
            for (ApiPlayerEntry entry : response.response()) {
                if (entry.player() != null
                        && entry.player().lastname() != null
                        && stripAccents(entry.player().lastname()).toLowerCase().equals(lastNameLc)) {
                    return Optional.of(entry.player().id());
                }
            }
            return Optional.empty();

        } catch (RestClientException e) {
            // 429 (rate limited), 500, or network blip — log and bail. The stats card stays hidden.
            log.warn("[ApiFootballService] searchPlayerId failed for '{}': {}", fullName, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Fetches a player's stats for a single season and converts them to our sport-agnostic DTO.
     *
     * API-SPORTS returns one entry per team the player represented that season (handles
     * mid-season transfers). We render each as a separate season row, with team abbreviation
     * carrying the difference.
     *
     * Free tier limitation: we can only get one season at a time. The "career" row stays null
     * — the DTO documentation explicitly allows this.
     */
    public PlayerCareerStatsDto fetchPlayerStats(int apiSportsPlayerId, int season) {
        try {
            ApiResponse response = restClient.get()
                    .uri(uri -> uri.path("/players")
                            .queryParam("id", apiSportsPlayerId)
                            .queryParam("season", season)
                            .build())
                    .retrieve()
                    .body(ApiResponse.class);

            if (response == null || response.response() == null || response.response().isEmpty()) {
                return null;
            }
            return toCareerStatsDto(response, season);

        } catch (RestClientException e) {
            log.warn("[ApiFootballService] fetchPlayerStats failed for id={} season={}: {}",
                    apiSportsPlayerId, season, e.getMessage());
            return null;
        }
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    // The single category we expose for football: a curated subset of API-SPORTS' enormous
    // stat array. We pick the columns a casual fan would recognise (apps, goals, assists,
    // shots, cards, rating) rather than the full ~60-field dump.
    private static final List<String> FOOTBALL_LABELS = List.of(
            "APPS", "MIN", "GOALS", "AST", "SHOTS", "ON", "PASS%", "YEL", "RED", "RATING"
    );

    private PlayerCareerStatsDto toCareerStatsDto(ApiResponse response, int season) {
        List<PlayerCareerStatsDto.SeasonRow> rows = new ArrayList<>();

        ApiPlayerEntry entry = response.response().get(0); // One entry per player ID
        if (entry.statistics() != null) {
            for (ApiStatBlock block : entry.statistics()) {
                rows.add(new PlayerCareerStatsDto.SeasonRow(
                        // Season label — e.g. "2024-25" reconstructed from the season int.
                        formatSeasonLabel(season),
                        block.team() != null ? block.team().name() : null,
                        // Competition name — what disambiguates the multiple rows API-Football
                        // returns per player-season ("Ligue 1", "UEFA Champions League", etc.).
                        block.league() != null ? block.league().name() : null,
                        valuesFor(block)
                ));
            }
        }

        if (rows.isEmpty()) return null;

        PlayerCareerStatsDto.StatCategory cat = new PlayerCareerStatsDto.StatCategory(
                "season",
                "Season",
                FOOTBALL_LABELS,
                rows,
                null  // free tier is single-season — no career aggregate
        );

        return new PlayerCareerStatsDto("football", List.of(cat));
    }

    // Pull the curated subset of fields out of the nested API-SPORTS structure,
    // formatted as strings (consistent with NBA/NFL rows which are also strings).
    // Nulls become "—" so the UI doesn't show literal "null".
    private List<String> valuesFor(ApiStatBlock block) {
        ApiGames games  = nullSafe(block.games());
        ApiShots shots  = nullSafe(block.shots());
        ApiPasses ps    = nullSafe(block.passes());
        ApiGoals goals  = nullSafe(block.goals());
        ApiCards cards  = nullSafe(block.cards());

        return List.of(
                str(games.appearences()),
                str(games.minutes()),
                str(goals.total()),
                str(goals.assists()),
                str(shots.total()),
                str(shots.on()),
                str(ps.accuracy(), "%"),
                str(cards.yellow()),
                str(cards.red()),
                str(games.rating())  // already a string in the API e.g. "8.04"
        );
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    // Decompose accented Latin letters into base + combining mark, then drop the marks.
    // "Dembélé" → "Dembele", "Müller" → "Muller", "Vinícius" → "Vinicius".
    // API-SPORTS' search field rejects diacritics outright, so this is mandatory for
    // most European squads. Pass-through for null and for already-clean ASCII strings.
    private static String stripAccents(String s) {
        if (s == null || s.isEmpty()) return s;
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    private static String safe(String s) { return s == null ? "" : s; }
    private static String str(Integer i) { return i == null ? "—" : String.valueOf(i); }
    private static String str(Integer i, String suffix) { return i == null ? "—" : i + suffix; }
    private static String str(String s)  { return s == null || s.isBlank() ? "—" : s; }

    private static String formatSeasonLabel(int season) {
        int end = (season + 1) % 100;
        return season + "-" + (end < 10 ? "0" + end : String.valueOf(end));
    }

    // Returns the input if non-null, otherwise an empty instance — keeps valuesFor() flat.
    private static ApiGames nullSafe(ApiGames g)  { return g  != null ? g  : new ApiGames(null, null, null, null); }
    private static ApiShots nullSafe(ApiShots s)  { return s  != null ? s  : new ApiShots(null, null); }
    private static ApiPasses nullSafe(ApiPasses p){ return p  != null ? p  : new ApiPasses(null, null, null); }
    private static ApiGoals nullSafe(ApiGoals g)  { return g  != null ? g  : new ApiGoals(null, null); }
    private static ApiCards nullSafe(ApiCards c)  { return c  != null ? c  : new ApiCards(null, null, null); }

    // ── API response records ─────────────────────────────────────────────────
    // Only the subset of API-SPORTS fields we actually use. @JsonIgnoreProperties means
    // their ~60-field stat array won't crash deserialisation when we ignore most of it.

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiResponse(List<ApiPlayerEntry> response) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiPlayerEntry(ApiPlayer player, List<ApiStatBlock> statistics) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiPlayer(Integer id, String firstname, String lastname) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiStatBlock(
            ApiTeam team,
            ApiLeague league,    // which competition this stat block belongs to
            ApiGames games,
            ApiShots shots,
            ApiGoals goals,
            ApiPasses passes,
            ApiCards cards)
    {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiTeam(Integer id, String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    // The competition this stat block is for — e.g. "Ligue 1", "UEFA Champions League".
    // Only `name` is used today; id / country / season are kept off the record to skip
    // unnecessary deserialisation work.
    public record ApiLeague(String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiGames(Integer appearences, Integer minutes, String rating, String position) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiShots(Integer total, Integer on) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiGoals(Integer total, Integer assists) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiPasses(Integer total, Integer key, Integer accuracy) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiCards(Integer yellow, Integer yellowred, Integer red) {}
}
