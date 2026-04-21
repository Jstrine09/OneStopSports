package com.matchday.service;

import com.matchday.dto.MatchDto;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MatchService {

    private final ExternalApiService externalApiService;

    @Cacheable("matches")
    public List<MatchDto> getLiveMatches() {
        return externalApiService.fetchLiveMatchDtos();
    }

    public List<MatchDto> getMatchesByLeagueAndDate(Long leagueId, LocalDate date) {
        // TODO: map leagueId → competition ID, call externalApiService.fetchMatchesByCompetition()
        //       and filter by date. Tracked in: TASK-16
        return Collections.emptyList();
    }

    public MatchDto getMatchById(Long id) {
        // TODO: return real data from ExternalApiService
        return null;
    }

    public List<Object> getMatchEvents(Long matchId) {
        // TODO: delegate to ExternalApiService once integrated
        return Collections.emptyList();
    }

    public Map<String, Object> getMatchStats(Long matchId) {
        // TODO: delegate to ExternalApiService once integrated
        return Map.of();
    }

    public Map<String, Object> getMatchLineups(Long matchId) {
        // TODO: delegate to ExternalApiService once integrated
        return Map.of();
    }
}
