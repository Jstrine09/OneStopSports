package com.onestopsports.controller;

import com.onestopsports.dto.LeagueDto;
import com.onestopsports.dto.StandingsEntryDto;
import com.onestopsports.dto.TeamDto;
import com.onestopsports.service.LeagueService;
import com.onestopsports.service.TeamService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/leagues")
public class LeagueController {

    private final LeagueService leagueService;
    private final TeamService teamService;

    public LeagueController(LeagueService leagueService, TeamService teamService) {
        this.leagueService = leagueService;
        this.teamService = teamService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<LeagueDto> getLeague(@PathVariable Long id) {
        return ResponseEntity.ok(leagueService.getLeagueById(id));
    }

    @GetMapping("/{id}/standings")
    public ResponseEntity<List<StandingsEntryDto>> getStandings(@PathVariable Long id) {
        return ResponseEntity.ok(leagueService.getStandings(id));
    }

    @GetMapping("/{id}/teams")
    public ResponseEntity<List<TeamDto>> getTeamsByLeague(@PathVariable Long id) {
        return ResponseEntity.ok(teamService.getTeamsByLeague(id));
    }
}
