package com.onestopsports.controller;

import com.onestopsports.dto.PlayerDto;
import com.onestopsports.dto.TeamDto;
import com.onestopsports.service.PlayerService;
import com.onestopsports.service.TeamService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Handles HTTP requests for a specific team — fetching its details and squad.
@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamService teamService;
    private final PlayerService playerService;

    public TeamController(TeamService teamService, PlayerService playerService) {
        this.teamService = teamService;
        this.playerService = playerService;
    }

    // GET /api/teams/{id}
    // Returns the team's basic info (name, crest, stadium, etc.)
    // Used to load the header section on TeamDetailPage.
    @GetMapping("/{id}")
    public ResponseEntity<TeamDto> getTeam(@PathVariable Long id) {
        return ResponseEntity.ok(teamService.getTeamById(id)); // Throws 404 if not found
    }

    // GET /api/teams/{id}/players
    // Returns all players on this team from our database.
    // Used to display the squad roster on TeamDetailPage, grouped by position.
    @GetMapping("/{id}/players")
    public ResponseEntity<List<PlayerDto>> getPlayers(@PathVariable Long id) {
        return ResponseEntity.ok(playerService.getPlayersByTeam(id));
    }
}
