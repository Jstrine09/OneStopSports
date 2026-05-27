package com.onestopsports.controller;

import com.onestopsports.dto.PlayerDto;
import com.onestopsports.dto.TeamDto;
import com.onestopsports.service.PlayerService;
import com.onestopsports.service.TeamService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    // GET /api/teams/{id}/players?season=2022
    //
    // Without ?season: returns the current squad from our database.
    //   Used to display the live roster on TeamDetailPage.
    //
    // With ?season=YYYY: returns the historical roster for that season from ESPN.
    //   season = start year of the season (e.g. 2022 for "2022-23") — ESPN convention.
    //   Works for NBA and NFL only — football-data.org free tier has no historical rosters.
    //   Returned PlayerDtos have null id — historical players may not exist in our DB.
    @GetMapping("/{id}/players")
    public ResponseEntity<List<PlayerDto>> getPlayers(
            @PathVariable Long id,
            @RequestParam(required = false) Integer season) {

        if (season != null) {
            // Delegate to TeamService which handles sport routing to the correct ESPN API
            return ResponseEntity.ok(teamService.getRosterForSeason(id, season));
        }

        // No season — serve the current roster from our own database
        return ResponseEntity.ok(playerService.getPlayersByTeam(id));
    }
}
