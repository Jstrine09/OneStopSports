package com.matchday.service;

import com.matchday.dto.MatchDto;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class MatchService {

    private final ExternalApiService externalApiService;

    public MatchService(ExternalApiService externalApiService) {
        this.externalApiService = externalApiService;
    }

    @Cacheable("matches")
    public List<MatchDto> getLiveMatches() {
        return externalApiService.fetchLiveMatchDtos();
    }

    public List<MatchDto> getMatchesByLeagueAndDate(Long leagueId, LocalDate date) {
        // TODO: map leagueId → competition ID, call externalApiService.fetchMatchesByCompetition()
        return Collections.emptyList();
    }

    public MatchDto getMatchById(Long id) {
        return null;
    }

    public List<Object> getMatchEvents(Long matchId) {
        return Collections.emptyList();
    }

    public Map<String, Object> getMatchStats(Long matchId) {
        return Map.of();
    }

    public Map<String, Object> getMatchLineups(Long matchId) {
        return Map.of();
    }
}
