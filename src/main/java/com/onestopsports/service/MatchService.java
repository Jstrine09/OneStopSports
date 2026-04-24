package com.onestopsports.service;

import com.onestopsports.dto.MatchDto;
import com.onestopsports.dto.MatchEventDto;
import com.onestopsports.repository.LeagueRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

// Handles business logic for Matches.
// Match data is NOT stored in our database — it's always fetched live from the relevant API.
// Football leagues → ExternalApiService (football-data.org)
// Basketball leagues → NbaApiService (balldontlie.io)
// Live matches are cached in Redis for 30 seconds to avoid hammering the API.
@Service
public class MatchService {

    private final ExternalApiService externalApiService;
    private final NbaApiService      nbaApiService;
    private final LeagueRepository   leagueRepository; // Used to look up a league before calling the right API

    public MatchService(ExternalApiService externalApiService,
                        NbaApiService nbaApiService,
                        LeagueRepository leagueRepository) {
        this.externalApiService = externalApiService;
        this.nbaApiService      = nbaApiService;
        this.leagueRepository   = leagueRepository;
    }

    // Returns all currently live matches across all competitions.
    // @Cacheable("matches") means the first call fetches from the API and stores the result in Redis.
    // Subsequent calls within 30 seconds return the cached result instead of hitting the API again.
    @Cacheable("matches")
    public List<MatchDto> getLiveMatches() {
        return externalApiService.fetchLiveMatchDtos();
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
}
