package com.matchday.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.matchday.dto.MatchDto;
import com.matchday.dto.StandingsEntryDto;
import com.matchday.dto.TeamDto;
import com.matchday.repository.LeagueRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

@Service
public class ExternalApiService {

    private final RestClient restClient;
    private final LeagueRepository leagueRepository;

    public ExternalApiService(
            @Value("${external-api.football-data.base-url}") String baseUrl,
            @Value("${external-api.football-data.api-key}") String apiKey,
            LeagueRepository leagueRepository) {

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Auth-Token", apiKey)
                .build();
        this.leagueRepository = leagueRepository;
    }

    // ── API Response Records (mirrors football-data.org v4 JSON) ─────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiTeamsResponse(ApiCompetition competition, List<ApiTeam> teams) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiCompetition(Integer id, String name, ApiArea area) {}

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

    /**
     * Fetch matches for a competition on a specific calendar date, mapped to MatchDto.
     */
    public List<MatchDto> fetchMatchDtosByCompetition(int competitionId, LocalDate date) {
        ApiMatchesResponse response = fetchMatchesByCompetitionAndDate(competitionId, date);
        if (response == null || response.matches() == null) {
            return Collections.emptyList();
        }
        return response.matches().stream()
                .map(this::toMatchDto)
                .toList();
    }

    private ApiMatchesResponse fetchMatchesByCompetitionAndDate(int competitionId, LocalDate date) {
        return restClient.get()
                .uri(b -> b.path("/competitions/{id}/matches")
                           .queryParam("dateFrom", date)
                           .queryParam("dateTo", date)
                           .build(competitionId))
                .retrieve()
                .body(ApiMatchesResponse.class);
    }

    // ── DTO conversion helpers (used by LeagueService / MatchService) ─────────

    public List<StandingsEntryDto> fetchStandings(Integer competitionId) {
        ApiStandingsResponse response = fetchStandingsByCompetition(competitionId);
        if (response == null || response.standings() == null) {
            return Collections.emptyList();
        }
        // football-data.org returns TOTAL, HOME, and AWAY groups — we want TOTAL
        return response.standings().stream()
                .filter(group -> "TOTAL".equals(group.type()))
                .findFirst()
                .map(group -> group.table().stream()
                        .map(this::toStandingsEntryDto)
                        .toList())
                .orElse(Collections.emptyList());
    }

    public List<MatchDto> fetchLiveMatchDtos() {
        ApiMatchesResponse response = fetchLiveMatches();
        if (response == null || response.matches() == null) {
            return Collections.emptyList();
        }
        return response.matches().stream()
                .map(this::toMatchDto)
                .toList();
    }

    // ── Private mappers ───────────────────────────────────────────────────────

    private MatchDto toMatchDto(ApiMatch m) {
        TeamDto home = new TeamDto(
                m.homeTeam().id(), m.homeTeam().name(), m.homeTeam().shortName(),
                m.homeTeam().crest(), null, null, null);

        TeamDto away = new TeamDto(
                m.awayTeam().id(), m.awayTeam().name(), m.awayTeam().shortName(),
                m.awayTeam().crest(), null, null, null);

        Integer homeScore = (m.score() != null && m.score().fullTime() != null)
                ? m.score().fullTime().home() : null;
        Integer awayScore = (m.score() != null && m.score().fullTime() != null)
                ? m.score().fullTime().away() : null;

        LocalDateTime startTime = null;
        if (m.utcDate() != null) {
            try {
                startTime = OffsetDateTime.parse(m.utcDate()).toLocalDateTime();
            } catch (Exception ignored) { }
        }

        Long dbLeagueId = null;
        if (m.competition() != null && m.competition().id() != null) {
            dbLeagueId = leagueRepository.findByExternalId(m.competition().id())
                    .map(league -> league.getId())
                    .orElse(null);
        }

        return new MatchDto(m.id(), home, away, homeScore, awayScore,
                m.status(), startTime, dbLeagueId);
    }

    private StandingsEntryDto toStandingsEntryDto(ApiStandingEntry entry) {
        TeamDto team = new TeamDto(
                entry.team().id(), entry.team().name(), entry.team().shortName(),
                entry.team().crest(), null, null, null);

        return new StandingsEntryDto(
                entry.position(),
                team,
                entry.playedGames(),
                entry.won(),
                entry.draw(),   // API uses "draw", DTO uses "drawn"
                entry.lost(),
                entry.goalsFor(),
                entry.goalsAgainst(),
                entry.points());
    }

    // ── Scheduled refresh (every 30 seconds) ─────────────────────────────────

    @Scheduled(fixedDelay = 30_000)
    public void refreshLiveMatchCache() {
        // TODO: call fetchLiveMatchDtos(), compare with cached data,
        //       push score changes via SimpMessagingTemplate to /topic/matches/live
        // Tracked in: TASK-17
    }
}
