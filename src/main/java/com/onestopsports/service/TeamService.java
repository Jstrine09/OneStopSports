package com.onestopsports.service;

import com.onestopsports.dto.TeamDto;
import com.onestopsports.model.Team;
import com.onestopsports.repository.TeamRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class TeamService {

    private final TeamRepository teamRepository;

    public TeamService(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

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
