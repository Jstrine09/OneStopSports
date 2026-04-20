package com.matchday.service;

import com.matchday.dto.TeamDto;
import com.matchday.model.Team;
import com.matchday.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;

    public TeamDto getTeamById(Long id) {
        return teamRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found: " + id));
    }

    public List<TeamDto> getTeamsByLeague(Long leagueId) {
        return teamRepository.findByLeagueId(leagueId).stream()
                .map(this::toDto)
                .toList();
    }

    TeamDto toDto(Team team) {
        return new TeamDto(
                team.getId(),
                team.getName(),
                team.getShortName(),
                team.getCrestUrl(),
                team.getStadium(),
                team.getCountry(),
                team.getLeague().getId());
    }
}
