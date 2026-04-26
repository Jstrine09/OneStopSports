package com.onestopsports.service;

import com.onestopsports.dto.MatchDto;
import com.onestopsports.dto.MatchEventDto;
import com.onestopsports.model.League;
import com.onestopsports.repository.LeagueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

// Handles business logic for Matches.
// Match data is NOT stored in our database — it's always fetched live from the relevant API.
// Football leagues → ExternalApiService (football-data.org)
// Basketball leagues → NbaApiService (balldontlie.io)
// Live matches are cached in Redis for 30 seconds to avoid hammering the API.
//
// This service also owns the live-score refresh scheduler — it runs every 30 seconds,
// checks both APIs, and pushes any score changes to connected WebSocket clients.
@Service
public class MatchService {

    private static final Logger log = LoggerFactory.getLogger(MatchService.class);

    private final ExternalApiService externalApiService;
    private final NbaApiService      nbaApiService;
    private final LeagueRepository   leagueRepository; // Used to look up a league before calling the right API

    // Pushes score updates to all connected WebSocket clients subscribed to /topic/matches/live
    private final SimpMessagingTemplate messagingTemplate;

    // Used to manually update the Redis "matches" cache when a score changes,
    // so the next REST request returns fresh data without an extra API call
    private final CacheManager cacheManager;

    // Tracks the last-seen state of every live match.
    // Key = match ID, Value = "homeScore:awayScore:status" snapshot string.
    // We only broadcast when something in this map changes — avoids flooding clients.
    // ConcurrentHashMap is thread-safe: the @Scheduled method runs on a background thread.
    private volatile Map<Long, String> previousSnapshot = new ConcurrentHashMap<>();

    public MatchService(ExternalApiService externalApiService,
                        NbaApiService nbaApiService,
                        LeagueRepository leagueRepository,
                        SimpMessagingTemplate messagingTemplate,
                        CacheManager cacheManager) {
        this.externalApiService = externalApiService;
        this.nbaApiService      = nbaApiService;
        this.leagueRepository   = leagueRepository;
        this.messagingTemplate  = messagingTemplate;
        this.cacheManager       = cacheManager;
    }

    // Returns all currently live matches across ALL sports (football + NBA).
    // @Cacheable("matches") means the first call fetches from the APIs and stores the result in Redis.
    // Subsequent calls within 30 seconds return the cached result without hitting the APIs again.
    @Cacheable("matches")
    public List<MatchDto> getLiveMatches() {
        // Football live matches from football-data.org
        List<MatchDto> football = externalApiService.fetchLiveMatchDtos();

        // NBA live matches — today's games filtered to those currently in progress
        List<MatchDto> nba = fetchNbaLiveMatches();

        // Combine both sports into one list for the frontend
        List<MatchDto> all = new ArrayList<>(football);
        all.addAll(nba);
        return all;
    }

    // Returns all matches for a league on a specific date.
    // Routes to the correct external API based on the league's sport:
    //   - "basketball" leagues → NbaApiService (balldontlie.io)
    //   - everything else     → ExternalApiService (football-data.org)
    //
    // @Transactional(readOnly = true) opens a database transaction so that
    // league.getSport() — which is lazily loaded — can be accessed safely.
    @Transactional(readOnly = true)
    public List<MatchDto> getMatchesByLeagueAndDate(Long leagueId, LocalDate date) {
        if (leagueId == null || date == null) return Collections.emptyList();

        return leagueRepository.findById(leagueId).map(league -> {
            // league.getSport() is a lazy-loaded relationship — works here because
            // we're inside a @Transactional method (Hibernate session stays open)
            String sportSlug = league.getSport().getSlug();

            return switch (sportSlug) {
                case "basketball" ->
                    // NBA: pass our DB league ID so returned MatchDtos can link back to this league
                    nbaApiService.fetchGameDtosByDate(date, league.getId());
                default ->
                    // Football: needs the football-data.org competition ID
                    league.getExternalId() != null
                        ? externalApiService.fetchMatchDtosByCompetition(league.getExternalId(), date)
                        : Collections.<MatchDto>emptyList();
            };
        }).orElse(Collections.emptyList());
    }

    // Returns a single match by its football-data.org match ID.
    // Used when someone opens a match detail page directly via URL (/matches/123)
    // rather than navigating from the scores list, where the match data was already
    // passed through router state and this method wouldn't be needed.
    // Returns null if the ID doesn't exist or the API returns nothing.
    public MatchDto getMatchById(Long id) {
        if (id == null) return null;
        return externalApiService.fetchMatchById(id);
    }

    // Returns all events (goals, cards, substitutions) for a specific match.
    // Calls football-data.org's match detail endpoint and parses the events out.
    public List<MatchEventDto> getMatchEvents(Long matchId) {
        if (matchId == null) return Collections.emptyList();
        return externalApiService.fetchMatchEventDtos(matchId);
    }

    // Match stats (possession, shots, etc.) are NOT available on the free tier of football-data.org.
    // Returns an empty map as a placeholder — shown as "coming soon" in the frontend.
    public Map<String, Object> getMatchStats(Long matchId) {
        return Map.of();
    }

    // Lineups are also NOT available on the free tier of football-data.org.
    // Returns an empty map as a placeholder — shown as "coming soon" in the frontend.
    public Map<String, Object> getMatchLineups(Long matchId) {
        return Map.of();
    }

    // ── Scheduled Tasks ───────────────────────────────────────────────────────

    // Runs every 30 seconds on a background thread.
    // Fetches the current live matches from ALL sports, compares them to the last known
    // state, and — if anything changed — pushes the updated list to all connected
    // WebSocket clients AND refreshes the Redis cache.
    //
    // Moved here from ExternalApiService so this service can own the full
    // "live matches = football + NBA" concept in one place.
    @Scheduled(fixedDelay = 30_000)
    public void refreshLiveMatchCache() {
        try {
            // Fetch live matches from both sports
            List<MatchDto> footballLive = externalApiService.fetchLiveMatchDtos();
            List<MatchDto> nbaLive      = fetchNbaLiveMatches();

            // Combine into one list
            List<MatchDto> current = new ArrayList<>(footballLive);
            current.addAll(nbaLive);

            // Build a snapshot: matchId → "homeScore:awayScore:status"
            // Null scores (scheduled matches) are represented as "null" in the string.
            Map<Long, String> snapshot = current.stream()
                    .collect(Collectors.toMap(
                            MatchDto::id,
                            m -> m.homeScore() + ":" + m.awayScore() + ":" + m.status()));

            // Only act if something actually changed since the last tick
            if (!snapshot.equals(previousSnapshot)) {
                previousSnapshot = snapshot; // Update our reference point for next tick

                // Write the fresh match list directly into the Redis cache.
                // SimpleKey.EMPTY is the cache key Spring uses for @Cacheable methods with no
                // arguments — i.e. getLiveMatches(). This means the next REST GET /matches/live
                // request returns this fresh data without making an extra API call.
                Cache cache = cacheManager.getCache("matches");
                if (cache != null) {
                    cache.put(SimpleKey.EMPTY, current);
                }

                // Broadcast the updated match list to every connected WebSocket client.
                // Spring's STOMP broker serialises the list to JSON automatically.
                messagingTemplate.convertAndSend("/topic/matches/live", current);
                log.info("[refreshLiveMatchCache] Score change detected — pushed {} live match(es) via WebSocket " +
                        "(football={}, nba={})", current.size(), footballLive.size(), nbaLive.size());
            }
        } catch (Exception e) {
            // Don't let a transient API failure kill the scheduler thread.
            // The next tick fires automatically after the delay.
            log.warn("[refreshLiveMatchCache] Failed to refresh: {}", e.getMessage());
        }
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    // Fetches today's NBA games and returns only the ones that are currently live.
    // Looks up every "basketball" league in the DB so it works even if more
    // basketball leagues are added in the future (e.g. EuroLeague).
    private List<MatchDto> fetchNbaLiveMatches() {
        List<MatchDto> liveGames = new ArrayList<>();

        // leagueRepository.findBySport_Slug generates a JOIN query — no lazy-loading issues
        List<League> basketballLeagues = leagueRepository.findBySport_Slug("basketball");

        for (League league : basketballLeagues) {
            // Fetch all of today's games for this league, then keep only the live ones.
            // NbaApiService.mapStatus() returns "LIVE" for in-progress games.
            nbaApiService.fetchGameDtosByDate(LocalDate.now(), league.getId())
                    .stream()
                    .filter(m -> "LIVE".equals(m.status()))
                    .forEach(liveGames::add);
        }

        return liveGames;
    }
}
