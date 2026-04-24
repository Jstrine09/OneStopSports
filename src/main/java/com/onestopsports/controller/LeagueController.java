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

// Handles HTTP requests for individual leagues — getting their details, standings, and teams.
@RestController
@RequestMapping("/api/leagues")
public class LeagueController {

    private final LeagueService leagueService;
    private final TeamService teamService;

    public LeagueController(LeagueService leagueService, TeamService teamService) {
        this.leagueService = leagueService;
        this.teamService = teamService;
    }

    // GET /api/leagues/{id}
    // Returns basic info about a single league (name, country, season, etc.)
    @GetMapping("/{id}")
    public ResponseEntity<LeagueDto> getLeague(@PathVariable Long id) {
        return ResponseEntity.ok(leagueService.getLeagueById(id)); // Throws 404 if not found
    }

    // GET /api/leagues/{id}/standings
    // Returns the current league table for this competition.
    // Data is fetched live from football-data.org (not stored in our DB — changes every match day).
    @GetMapping("/{id}/standings")
    public ResponseEntity<List<StandingsEntryDto>> getStandings(@PathVariable Long id) {
        return ResponseEntity.ok(leagueService.getStandings(id));
    }

    // GET /api/leagues/{id}/teams
    // Returns all teams in this league from our own database.
    // Used by the Teams tab in LeaguesPage and to link team cards to /teams/{id}.
    @GetMapping("/{id}/teams")
    public ResponseEntity<List<TeamDto>> getTeamsByLeague(@PathVariable Long id) {
        return ResponseEntity.ok(teamService.getTeamsByLeague(id));
    }
}
