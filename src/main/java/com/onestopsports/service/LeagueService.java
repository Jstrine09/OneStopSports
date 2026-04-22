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

@Service
public class LeagueService {

    private final LeagueRepository leagueRepository;
    private final ExternalApiService externalApiService;

    public LeagueService(LeagueRepository leagueRepository, ExternalApiService externalApiService) {
        this.leagueRepository = leagueRepository;
        this.externalApiService = externalApiService;
    }

    public List<LeagueDto> getLeaguesBySport(Long sportId) {
        return leagueRepository.findBySportId(sportId).stream()
                .map(this::toDto)
                .toList();
    }

    public LeagueDto getLeagueById(Long id) {
        return leagueRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "League not found: " + id));
    }

    public List<StandingsEntryDto> getStandings(Long leagueId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "League not found: " + leagueId));
        if (league.getExternalId() == null) {
            return Collections.emptyList();
        }
        return externalApiService.fetchStandings(league.getExternalId());
    }

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
