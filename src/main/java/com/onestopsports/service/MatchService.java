package com.onestopsports.service;

import com.onestopsports.dto.MatchDto;
import com.onestopsports.dto.MatchEventDto;
import com.onestopsports.repository.LeagueRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class MatchService {

    private final ExternalApiService externalApiService;
    private final LeagueRepository leagueRepository;

    public MatchService(ExternalApiService externalApiService, LeagueRepository leagueRepository) {
        this.externalApiService = externalApiService;
        this.leagueRepository = leagueRepository;
    }

    @Cacheable("matches")
    public List<MatchDto> getLiveMatches() {
        return externalApiService.fetchLiveMatchDtos();
    }

    public List<MatchDto> getMatchesByLeagueAndDate(Long leagueId, LocalDate date) {
        if (leagueId == null || date == null) return Collections.emptyList();
        return leagueRepository.findById(leagueId)
                .filter(l -> l.getExternalId() != null)
                .map(l -> externalApiService.fetchMatchDtosByCompetition(l.getExternalId(), date))
                .orElse(Collections.emptyList());
    }

    public MatchDto getMatchById(Long id) {
        return null;
    }

    public List<MatchEventDto> getMatchEvents(Long matchId) {
        if (matchId == null) return Collections.emptyList();
        return externalApiService.fetchMatchEventDtos(matchId);
    }

    public Map<String, Object> getMatchStats(Long matchId) {
        return Map.of();
    }

    public Map<String, Object> getMatchLineups(Long matchId) {
        return Map.of();
    }
}
