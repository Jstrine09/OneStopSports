package com.onestopsports.service;

import com.onestopsports.dto.LeagueDto;
import com.onestopsports.dto.StandingsEntryDto;
import com.onestopsports.model.League;
import com.onestopsports.repository.LeagueRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;

// Handles business logic for Leagues.
// For standings, it delegates to the appropriate external API:
//   - Football leagues       → ExternalApiService (football-data.org)
//   - Basketball leagues     → NbaApiService (balldontlie.io)
//   - American football (NFL) → NflApiService (ESPN unofficial API)
// Standings aren't stored in our database — they're always fetched live.

// Handles business logic for Leagues.
// For standings, it delegates to the appropriate external API:
//   - Football leagues  → ExternalApiService (football-data.org)
//   - Basketball leagues → NbaApiService (balldontlie.io)
// Standings aren't stored in our database — they're always fetched live.
@Service
public class LeagueService {

    private final LeagueRepository   leagueRepository;
    private final ExternalApiService externalApiService; // Football (soccer) API
    private final NbaApiService      nbaApiService;      // NBA API
    private final NflApiService      nflApiService;      // NFL API (ESPN)

    public LeagueService(LeagueRepository leagueRepository,
                         ExternalApiService externalApiService,
                         NbaApiService nbaApiService,
                         NflApiService nflApiService) {
        this.leagueRepository   = leagueRepository;
        this.externalApiService = externalApiService;
        this.nbaApiService      = nbaApiService;
        this.nflApiService      = nflApiService;
    }

    // Returns all leagues that belong to a given sport.
    // Called by GET /api/sports/{slug}/leagues
    public List<LeagueDto> getLeaguesBySport(Long sportId) {
        return leagueRepository.findBySportId(sportId).stream()
                .map(this::toDto)
                .toList();
    }

    // Returns a single league by its database ID, or throws 404 if it doesn't exist.
    public LeagueDto getLeagueById(Long id) {
        return leagueRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "League not found: " + id));
    }

    // Returns the current standings table for a league.
    // Routes to the correct API based on the league's sport:
    //   - "basketball" → NbaApiService.fetchStandings()
    //   - default (football) → ExternalApiService.fetchStandings() using the competition's externalId
    public List<StandingsEntryDto> getStandings(Long leagueId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "League not found: " + leagueId));

        // league.getSport() is lazy — Spring Boot's Open Session In View keeps the
        // Hibernate session alive for the duration of the HTTP request, so this is safe
        String sportSlug = league.getSport().getSlug();

        if ("basketball".equals(sportSlug)) {
            // NBA: balldontlie standings (returns empty on free tier — that's expected)
            return nbaApiService.fetchStandings(league.getId());
        }

        if ("american-football".equals(sportSlug)) {
            // NFL: ESPN standings — returns empty in the off-season (May–August)
            return nflApiService.fetchStandings(league.getId());
        }

        // Football (soccer) path — requires the football-data.org competition ID
        if (league.getExternalId() == null) {
            return Collections.emptyList();
        }
        return externalApiService.fetchStandings(league.getExternalId());
    }

    // Converts a League database entity to a LeagueDto for the frontend.
    // Note: league.getSport().getId() triggers a lazy load of the Sport — that's expected here.
    private LeagueDto toDto(League league) {
        return new LeagueDto(
                league.getId(),
                league.getName(),
                league.getCountry(),
                league.getLogoUrl(),
                league.getSeason(),
                league.getSport().getId(),
                league.getExternalId());
    }
}
