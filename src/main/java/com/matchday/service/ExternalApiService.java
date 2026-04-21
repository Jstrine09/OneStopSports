package com.matchday.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.matchday.dto.MatchDto;
import com.matchday.dto.StandingsEntryDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;

@Service
public class ExternalApiService {

    private final RestClient restClient;

    public ExternalApiService(
            @Value("${external-api.football-data.base-url}") String baseUrl,
            @Value("${external-api.football-data.api-key}") String apiKey) {

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Auth-Token", apiKey)
                .build();
    }

    // ── API Response Records (mirrors football-data.org v4 JSON) ─────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiTeamsResponse(ApiCompetition competition, List<ApiTeam> teams) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiCompetition(String name, ApiArea area) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiArea(String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiTeam(Long id, String name, String shortName, String venue,
                          Integer founded, String crest,
                          ApiCoach coach, List<ApiPlayer> squad) {}

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
    public record ApiScore(ApiFullTime fullTime) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiFullTime(Integer home, Integer away) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiStandingsResponse(ApiCompetition competition,
                                       List<ApiStandingGroup> standings) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiStandingGroup(String type, List<ApiStandingEntry> table) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiStandingEntry(Integer position, ApiMatchTeam team, Integer playedGames,
                                   Integer won, Integer draw, Integer lost,
                                   Integer goalsFor, Integer goalsAgainst, Integer points) {}

    // ── API Calls ─────────────────────────────────────────────────────────────

    /**
     * Fetch all teams (with squad) for a competition.
     * e.g. PL=2021, La Liga=2014, Bundesliga=2002, UCL=2001
     */
    public ApiTeamsResponse fetchTeamsByCompetition(int competitionId) {
        return restClient.get()
                .uri("/competitions/{id}/teams", competitionId)
                .retrieve()
                .body(ApiTeamsResponse.class);
    }

    /**
     * Fetch current standings table for a competition.
     */
    public ApiStandingsResponse fetchStandingsByCompetition(int competitionId) {
        return restClient.get()
                .uri("/competitions/{id}/standings", competitionId)
                .retrieve()
                .body(ApiStandingsResponse.class);
    }

    /**
     * Fetch all currently live matches across all competitions.
     */
    public ApiMatchesResponse fetchLiveMatches() {
        return restClient.get()
                .uri("/matches?status=LIVE")
                .retrieve()
                .body(ApiMatchesResponse.class);
    }

    /**
     * Fetch matches for a specific competition (optionally filtered by date range).
     */
    public ApiMatchesResponse fetchMatchesByCompetition(int competitionId) {
        return restClient.get()
                .uri("/competitions/{id}/matches", competitionId)
                .retrieve()
                .body(ApiMatchesResponse.class);
    }

    // ── DTO conversion helpers (used by LeagueService / MatchService) ─────────

    public List<StandingsEntryDto> fetchStandings(Long leagueId) {
        // TODO: map leagueId → competition ID using a lookup table or stored field,
        //       then call fetchStandingsByCompetition() and convert entries to StandingsEntryDto.
        // Tracked in: TASK-15
        return Collections.emptyList();
    }

    public List<MatchDto> fetchLiveMatchDtos() {
        // TODO: call fetchLiveMatches() and map ApiMatch → MatchDto.
        // Tracked in: TASK-16
        return Collections.emptyList();
    }

    // ── Scheduled refresh (every 30 seconds) ─────────────────────────────────

    @Scheduled(fixedDelay = 30_000)
    public void refreshLiveMatchCache() {
        // TODO: call fetchLiveMatchDtos(), compare with cached data,
        //       push score changes via SimpMessagingTemplate to /topic/matches/live
        // Tracked in: TASK-17
    }
}
