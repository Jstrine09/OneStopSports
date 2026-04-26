package com.onestopsports.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.onestopsports.dto.MatchDto;
import com.onestopsports.dto.MatchEventDto;
import com.onestopsports.dto.StandingsEntryDto;
import com.onestopsports.dto.TeamDto;
import com.onestopsports.repository.LeagueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

// This service is the only part of the app that talks to the football-data.org API.
// Everything else (matches, standings, events) goes through here.
// It uses RestClient (Spring 6's synchronous HTTP client) to make the API calls.
//
// API base URL: https://api.football-data.org/v4
// Rate limit on the free tier: 10 requests per minute
@Service
public class ExternalApiService {

    private static final Logger log = LoggerFactory.getLogger(ExternalApiService.class);

    // The pre-configured HTTP client — has the base URL and API key baked in
    private final RestClient restClient;

    // Used to convert football-data.org competition IDs into our internal DB league IDs
    private final LeagueRepository leagueRepository;

    // Pushes messages to all connected WebSocket clients subscribed to /topic/matches/live
    private final SimpMessagingTemplate messagingTemplate;

    // Used to manually update the Redis "matches" cache entry after a score change,
    // so the next REST call to GET /matches/live returns fresh data without an extra API call
    private final CacheManager cacheManager;

    // Tracks the last-seen score state for every live match.
    // Key = match ID, Value = "homeScore:awayScore:status" snapshot string.
    // If any entry differs between ticks, we know a score or status has changed.
    // ConcurrentHashMap is thread-safe — the @Scheduled method runs on a background thread.
    private volatile Map<Long, String> previousSnapshot = new ConcurrentHashMap<>();

    // Values are injected from application-local.yml (the file that's gitignored for security)
    public ExternalApiService(
            @Value("${external-api.football-data.base-url}") String baseUrl,
            @Value("${external-api.football-data.api-key}") String apiKey,
            LeagueRepository leagueRepository,
            SimpMessagingTemplate messagingTemplate,
            CacheManager cacheManager) {

        // Build the RestClient once at startup with the base URL and auth header pre-set.
        // Every API call will automatically include "X-Auth-Token: <your key>".
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Auth-Token", apiKey)
                .build();
        this.leagueRepository   = leagueRepository;
        this.messagingTemplate  = messagingTemplate;
        this.cacheManager       = cacheManager;
    }

    // ── API Response Records ──────────────────────────────────────────────────
    // These are Java records that mirror the JSON structure football-data.org returns.
    // @JsonIgnoreProperties(ignoreUnknown = true) means if the API adds new fields,
    // we won't crash — we just ignore anything we didn't ask for.

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiTeamsResponse(ApiCompetition competition, List<ApiTeam> teams) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiCompetition(Integer id, String name, ApiArea area) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiArea(String name) {} // e.g. { "name": "England" }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiTeam(Long id, String name, String shortName, String venue,
                          Integer founded, String crest,
                          ApiCoach coach, List<ApiPlayer> squad) {} // squad = list of players on the team

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiCoach(String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiPlayer(Long id, String name, String position,
                            Integer shirtNumber, String nationality,
                            String dateOfBirth) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiMatchesResponse(List<ApiMatch> matches) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiMatch(Long id, ApiMatchTeam homeTeam, ApiMatchTeam awayTeam,
                           ApiScore score, String status, String utcDate,
                           ApiCompetition competition) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiMatchTeam(Long id, String name, String shortName, String crest) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiScore(ApiFullTime fullTime) {} // fullTime holds the score at full time

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiFullTime(Integer home, Integer away) {} // e.g. { "home": 2, "away": 1 }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiStandingsResponse(ApiCompetition competition,
                                       List<ApiStandingGroup> standings) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    // The API returns standings split into TOTAL, HOME, and AWAY groups — we only use TOTAL
    public record ApiStandingGroup(String type, List<ApiStandingEntry> table) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiStandingEntry(Integer position, ApiMatchTeam team, Integer playedGames,
                                   Integer won, Integer draw, Integer lost,
                                   Integer goalsFor, Integer goalsAgainst, Integer points) {}

    // ── Match Detail Records (for events) ────────────────────────────────────
    // Returned by GET /matches/{id} — richer than the basic ApiMatch

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiMatchDetail(Long id, ApiMatchTeam homeTeam, ApiMatchTeam awayTeam,
                                 ApiScore score, String status, String utcDate,
                                 ApiCompetition competition,
                                 List<ApiGoal> goals,           // All goals scored
                                 List<ApiBooking> bookings,     // Yellow/red cards
                                 List<ApiSubstitution> substitutions) {} // Player swaps

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiGoal(Integer minute, Integer injuryTime, String type, // type = "REGULAR", "OWN_GOAL", or "PENALTY"
                          ApiMatchTeam team, ApiPlayerRef scorer, ApiPlayerRef assist) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiBooking(Integer minute, ApiMatchTeam team,
                             ApiPlayerRef player, String card) {} // card = "YELLOW_CARD", "RED_CARD", etc.

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiSubstitution(Integer minute, ApiMatchTeam team,
                                  ApiPlayerRef playerOut, ApiPlayerRef playerIn) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiPlayerRef(Long id, String name) {} // Minimal player info used inside events

    // ── API Calls ─────────────────────────────────────────────────────────────

    /**
     * Fetches full team data for a single team by its football-data.org ID.
     * Used as a fallback when the competition teams endpoint doesn't include squad data
     * (common for UCL teams which aren't "registered" at the competition level).
     */
    public ApiTeam fetchTeamById(Long teamId) {
        return restClient.get()
                .uri("/teams/{id}", teamId) // GET /teams/65 for example
                .retrieve()
                .body(ApiTeam.class); // Deserialise the JSON response into an ApiTeam record
    }

    /**
     * Fetches all teams (with their squad lists) for a competition.
     * Used by DataLoader at startup to seed the database.
     * e.g. competitionId 2021 = Premier League, 2001 = Champions League
     */
    public ApiTeamsResponse fetchTeamsByCompetition(int competitionId) {
        return restClient.get()
                .uri("/competitions/{id}/teams", competitionId)
                .retrieve()
                .body(ApiTeamsResponse.class);
    }

    /**
     * Fetches the current standings table for a competition.
     */
    public ApiStandingsResponse fetchStandingsByCompetition(int competitionId) {
        return restClient.get()
                .uri("/competitions/{id}/standings", competitionId)
                .retrieve()
                .body(ApiStandingsResponse.class);
    }

    /**
     * Fetches all currently live matches across all competitions on the platform.
     */
    public ApiMatchesResponse fetchLiveMatches() {
        return restClient.get()
                .uri("/matches?status=LIVE")
                .retrieve()
                .body(ApiMatchesResponse.class);
    }

    /**
     * Fetches all matches for a competition (no date filter — returns the entire season).
     */
    public ApiMatchesResponse fetchMatchesByCompetition(int competitionId) {
        return restClient.get()
                .uri("/competitions/{id}/matches", competitionId)
                .retrieve()
                .body(ApiMatchesResponse.class);
    }

    /**
     * Fetches matches for a competition on a specific date, already converted to MatchDtos.
     * Used by GET /api/matches?league={id}&date={date}
     */
    public List<MatchDto> fetchMatchDtosByCompetition(int competitionId, LocalDate date) {
        ApiMatchesResponse response = fetchMatchesByCompetitionAndDate(competitionId, date);
        if (response == null || response.matches() == null) {
            return Collections.emptyList();
        }
        return response.matches().stream()
                .map(this::toMatchDto) // Convert each raw API match into a MatchDto
                .toList();
    }

    // Calls the API with dateFrom and dateTo set to the same date to get matches on one specific day.
    private ApiMatchesResponse fetchMatchesByCompetitionAndDate(int competitionId, LocalDate date) {
        return restClient.get()
                .uri(b -> b.path("/competitions/{id}/matches")
                           .queryParam("dateFrom", date) // e.g. 2024-04-23
                           .queryParam("dateTo", date)   // Same date = only matches on this day
                           .build(competitionId))
                .retrieve()
                .body(ApiMatchesResponse.class);
    }

    // ── DTO Conversion Methods ────────────────────────────────────────────────

    /**
     * Fetches standings for a competition and returns them as a list of StandingsEntryDtos.
     * Filters to the TOTAL standings group (the API also returns HOME and AWAY splits).
     */
    public List<StandingsEntryDto> fetchStandings(Integer competitionId) {
        ApiStandingsResponse response = fetchStandingsByCompetition(competitionId);
        if (response == null || response.standings() == null) {
            return Collections.emptyList();
        }

        // football-data.org returns TOTAL, HOME, and AWAY groups — we only want TOTAL
        return response.standings().stream()
                .filter(group -> "TOTAL".equals(group.type()))
                .findFirst()
                .map(group -> group.table().stream()
                        .map(this::toStandingsEntryDto)
                        .toList())
                .orElse(Collections.emptyList());
    }

    /**
     * Fetches all live matches and converts them to MatchDtos.
     * Called by MatchService.getLiveMatches() which caches the result in Redis.
     */
    public List<MatchDto> fetchLiveMatchDtos() {
        ApiMatchesResponse response = fetchLiveMatches();
        if (response == null || response.matches() == null) {
            return Collections.emptyList();
        }
        return response.matches().stream()
                .map(this::toMatchDto)
                .toList();
    }

    /**
     * Fetches all events for a specific match (goals, cards, substitutions),
     * merges them into a single list, and sorts them by minute.
     * Called by MatchService.getMatchEvents() for the match timeline.
     */
    public List<MatchEventDto> fetchMatchEventDtos(Long matchId) {
        // GET /matches/{id} returns the full match detail including events
        ApiMatchDetail detail = restClient.get()
                .uri("/matches/{id}", matchId)
                .retrieve()
                .body(ApiMatchDetail.class);

        if (detail == null) return Collections.emptyList();

        List<MatchEventDto> events = new ArrayList<>();

        // Process goals — distinguish regular goals from own goals and penalties
        if (detail.goals() != null) {
            for (ApiGoal g : detail.goals()) {
                // Map the API's goal type to our DTO's type string
                String type = "OWN_GOAL".equals(g.type()) ? "OWN_GOAL"
                            : "PENALTY".equals(g.type()) ? "PENALTY" : "GOAL";
                events.add(new MatchEventDto(
                        type,
                        g.minute(),
                        g.injuryTime(),
                        g.scorer() != null ? g.scorer().name() : null, // Scorer's name
                        g.assist()  != null ? g.assist().name()  : null, // Assister's name (may be null)
                        g.team()    != null ? g.team().name()    : null));
            }
        }

        // Process bookings — the card type ("YELLOW_CARD" etc.) becomes the event type
        if (detail.bookings() != null) {
            for (ApiBooking b : detail.bookings()) {
                events.add(new MatchEventDto(
                        b.card(),   // e.g. "YELLOW_CARD", "RED_CARD"
                        b.minute(),
                        null,       // No injury time for bookings
                        b.player() != null ? b.player().name() : null,
                        null,       // No assist for bookings
                        b.team()   != null ? b.team().name()   : null));
            }
        }

        // Process substitutions — playerName = coming off, assistName = coming on
        if (detail.substitutions() != null) {
            for (ApiSubstitution s : detail.substitutions()) {
                events.add(new MatchEventDto(
                        "SUBSTITUTION",
                        s.minute(),
                        null,
                        s.playerOut() != null ? s.playerOut().name() : null, // Player leaving
                        s.playerIn()  != null ? s.playerIn().name()  : null, // Player entering
                        s.team()      != null ? s.team().name()      : null));
            }
        }

        // Sort all events by the minute they occurred so the timeline is in order
        events.sort(Comparator.comparingInt(e -> e.minute() != null ? e.minute() : 0));
        return events;
    }

    /**
     * Fetches a single match by its football-data.org match ID and returns it as a MatchDto.
     * Returns null if the API returns nothing (e.g. unknown ID).
     *
     * We already call GET /matches/{id} in fetchMatchEventDtos() to get the event timeline.
     * This method reuses the same endpoint but converts the response to a MatchDto instead,
     * so MatchService.getMatchById() has a real result to return.
     */
    public MatchDto fetchMatchById(Long matchId) {
        // GET /matches/{id} returns the full ApiMatchDetail which has all the same
        // base fields as ApiMatch (id, teams, score, status, utcDate, competition)
        // plus the events lists we parse separately in fetchMatchEventDtos.
        ApiMatchDetail detail = restClient.get()
                .uri("/matches/{id}", matchId)
                .retrieve()
                .body(ApiMatchDetail.class);

        if (detail == null) return null;
        return toMatchDtoFromDetail(detail);
    }

    // ── Private Mapper Methods ────────────────────────────────────────────────

    // Converts a raw API match object into the MatchDto the frontend expects.
    private MatchDto toMatchDto(ApiMatch m) {
        // Build minimal TeamDtos from the match data (these only have ID, name, short name, crest)
        // Stadium, country, leagueId are null here — full team data is loaded separately if needed
        TeamDto home = new TeamDto(
                m.homeTeam().id(), m.homeTeam().name(), m.homeTeam().shortName(),
                m.homeTeam().crest(), null, null, null);

        TeamDto away = new TeamDto(
                m.awayTeam().id(), m.awayTeam().name(), m.awayTeam().shortName(),
                m.awayTeam().crest(), null, null, null);

        // Score may be null for scheduled matches (game hasn't started)
        Integer homeScore = (m.score() != null && m.score().fullTime() != null)
                ? m.score().fullTime().home() : null;
        Integer awayScore = (m.score() != null && m.score().fullTime() != null)
                ? m.score().fullTime().away() : null;

        // The API returns time as a UTC string — convert it to a LocalDateTime
        LocalDateTime startTime = null;
        if (m.utcDate() != null) {
            try {
                startTime = OffsetDateTime.parse(m.utcDate()).toLocalDateTime();
            } catch (Exception ignored) { }
        }

        // Translate the football-data.org competition ID into our internal DB league ID
        // so the frontend can navigate to the correct league page
        Long dbLeagueId = null;
        if (m.competition() != null && m.competition().id() != null) {
            dbLeagueId = leagueRepository.findByExternalId(m.competition().id())
                    .map(league -> league.getId())
                    .orElse(null); // null if this competition isn't in our DB yet
        }

        return new MatchDto(m.id(), home, away, homeScore, awayScore,
                m.status(), startTime, dbLeagueId);
    }

    // Converts an ApiMatchDetail (returned by GET /matches/{id}) into a MatchDto.
    // ApiMatchDetail has exactly the same base fields as ApiMatch — id, homeTeam, awayTeam,
    // score, status, utcDate, competition — so the conversion logic is identical to toMatchDto().
    // The only difference is that ApiMatchDetail also carries event lists (goals, bookings,
    // substitutions) which we don't need here — those are handled by fetchMatchEventDtos().
    private MatchDto toMatchDtoFromDetail(ApiMatchDetail detail) {
        TeamDto home = new TeamDto(
                detail.homeTeam().id(), detail.homeTeam().name(), detail.homeTeam().shortName(),
                detail.homeTeam().crest(), null, null, null);

        TeamDto away = new TeamDto(
                detail.awayTeam().id(), detail.awayTeam().name(), detail.awayTeam().shortName(),
                detail.awayTeam().crest(), null, null, null);

        // Score is null for scheduled matches — guard against NPE before reading the goals
        Integer homeScore = (detail.score() != null && detail.score().fullTime() != null)
                ? detail.score().fullTime().home() : null;
        Integer awayScore = (detail.score() != null && detail.score().fullTime() != null)
                ? detail.score().fullTime().away() : null;

        // Parse the UTC ISO string into a LocalDateTime for the frontend
        LocalDateTime startTime = null;
        if (detail.utcDate() != null) {
            try {
                startTime = OffsetDateTime.parse(detail.utcDate()).toLocalDateTime();
            } catch (Exception ignored) { }
        }

        // Map the football-data.org competition ID to our internal DB league ID
        // so the frontend can link back to the correct league page
        Long dbLeagueId = null;
        if (detail.competition() != null && detail.competition().id() != null) {
            dbLeagueId = leagueRepository.findByExternalId(detail.competition().id())
                    .map(league -> league.getId())
                    .orElse(null);
        }

        return new MatchDto(detail.id(), home, away, homeScore, awayScore,
                detail.status(), startTime, dbLeagueId);
    }

    // Converts one row of standings data from the API into a StandingsEntryDto.
    private StandingsEntryDto toStandingsEntryDto(ApiStandingEntry entry) {
        // Again, minimal TeamDto — only the fields available in standings data
        TeamDto team = new TeamDto(
                entry.team().id(), entry.team().name(), entry.team().shortName(),
                entry.team().crest(), null, null, null);

        return new StandingsEntryDto(
                entry.position(),
                team,
                entry.playedGames(),
                entry.won(),
                entry.draw(),   // Note: the API field is "draw" but our DTO field is "drawn"
                entry.lost(),
                entry.goalsFor(),
                entry.goalsAgainst(),
                entry.points());
    }

    // ── Scheduled Tasks ───────────────────────────────────────────────────────

    // Runs every 30 seconds on a background thread (Spring's task scheduler).
    // Fetches the current live matches from football-data.org, compares them to the
    // last known state, and — if anything changed — pushes the updated list to all
    // connected WebSocket clients and refreshes the Redis cache.
    //
    // Why compare before pushing?
    // Pushing on every tick would flood clients even when nothing has changed.
    // We only push when a score or match status actually changes.
    @Scheduled(fixedDelay = 30_000)
    public void refreshLiveMatchCache() {
        try {
            List<MatchDto> current = fetchLiveMatchDtos();

            // Build a snapshot: matchId → "homeScore:awayScore:status"
            // Null scores (scheduled matches) are represented as "null" in the string
            Map<Long, String> snapshot = current.stream()
                    .collect(Collectors.toMap(
                            MatchDto::id,
                            m -> m.homeScore() + ":" + m.awayScore() + ":" + m.status()));

            // Only act if something actually changed since the last check
            if (!snapshot.equals(previousSnapshot)) {
                previousSnapshot = snapshot; // Update our reference point for next tick

                // Write the fresh match list directly into the Redis cache.
                // SimpleKey.EMPTY is the key Spring uses for @Cacheable methods with no arguments —
                // i.e. MatchService.getLiveMatches(). This avoids an extra API call on the next REST request.
                Cache cache = cacheManager.getCache("matches");
                if (cache != null) {
                    cache.put(SimpleKey.EMPTY, current);
                }

                // Broadcast the updated match list to every client subscribed to this topic.
                // Spring's STOMP broker serialises the list to JSON automatically.
                messagingTemplate.convertAndSend("/topic/matches/live", current);
                log.info("[refreshLiveMatchCache] Score change detected — pushed {} live matches via WebSocket",
                        current.size());
            }
        } catch (Exception e) {
            // Don't let a transient API failure crash the scheduler thread.
            // The next tick will retry automatically.
            log.warn("[refreshLiveMatchCache] Failed to refresh: {}", e.getMessage());
        }
    }
}
