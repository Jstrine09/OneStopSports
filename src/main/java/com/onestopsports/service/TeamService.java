package com.onestopsports.service;

import com.onestopsports.dto.TeamDto;
import com.onestopsports.model.Team;
import com.onestopsports.repository.TeamRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

// Handles business logic for Teams.
// All data comes from our own database (seeded from football-data.org at startup).
@Service
public class TeamService {

    private final TeamRepository teamRepository;

    public TeamService(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    // Returns a single team by its database ID, or throws 404 if not found.
    // Called by GET /api/teams/{id} — used to load the team header on TeamDetailPage.
    public TeamDto getTeamById(Long id) {
        return teamRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found: " + id));
    }

    // Returns all teams in a given league.
    // Called by GET /api/leagues/{id}/teams — used for the Teams tab in LeaguesPage.
    public List<TeamDto> getTeamsByLeague(Long leagueId) {
        return teamRepository.findByLeagueId(leagueId).stream()
                .map(this::toDto)
                .toList();
    }

    // Package-private (no access modifier) so UserService can also use it
    // to convert FavoriteTeam entities into TeamDtos without duplicating logic.
    TeamDto toDto(Team team) {
        return new TeamDto(
                team.getId(),
                team.getName(),
                team.getShortName(),
                team.getCrestUrl(),
                team.getStadium(),
                team.getCountry(),
                team.getLeague().getId()); // Triggers a lazy load of the League — fine here
    }
}
