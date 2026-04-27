package com.onestopsports.controller;

import com.onestopsports.dto.SearchResultDto;
import com.onestopsports.service.PlayerService;
import com.onestopsports.service.TeamService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Handles global search across all sports — finds teams and players by name.
// Returns both in a single response so the frontend can show a unified results page.
// This is a public endpoint — no authentication required to search.
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final TeamService   teamService;
    private final PlayerService playerService;

    public SearchController(TeamService teamService, PlayerService playerService) {
        this.teamService   = teamService;
        this.playerService = playerService;
    }

    // GET /api/search?q=messi
    // Returns up to 8 matching teams and 10 matching players (case-insensitive partial match).
    //
    // Minimum 2 characters — single-letter searches would return too many results to be useful,
    // and this guards against accidental full-table scans on very short queries.
    @GetMapping
    public ResponseEntity<SearchResultDto> search(@RequestParam String q) {
        // Blank or too-short query — return empty results rather than an error
        if (q == null || q.isBlank() || q.length() < 2) {
            return ResponseEntity.ok(new SearchResultDto(List.of(), List.of()));
        }

        // Run both searches and bundle the results together
        return ResponseEntity.ok(new SearchResultDto(
                teamService.searchTeams(q),
                playerService.searchPlayers(q)));
    }
}
