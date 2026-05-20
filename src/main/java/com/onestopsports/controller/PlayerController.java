package com.onestopsports.controller;

import com.onestopsports.dto.PlayerBioDto;
import com.onestopsports.dto.PlayerDto;
import com.onestopsports.service.PlayerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Handles HTTP requests for individual players.
@RestController
@RequestMapping("/api/players")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    // GET /api/players/{id}
    // Returns a single player's details.
    // Used by PlayerDetailPage when navigating directly to a player URL
    // (e.g. /players/42 in the browser — without router state from clicking a link).
    @GetMapping("/{id}")
    public ResponseEntity<PlayerDto> getPlayer(@PathVariable Long id) {
        return ResponseEntity.ok(playerService.getPlayerById(id)); // Throws 404 if not found
    }

    // GET /api/players/{id}/bio
    // Enrichment endpoint — fetches biographical data from balldontlie.io for NBA players.
    // Returns 200 with body when bio data is found, 204 No Content when not found
    // (e.g. for football or NFL players who aren't in the NBA database).
    // The frontend handles 204 gracefully by simply hiding the bio section.
    @GetMapping("/{id}/bio")
    public ResponseEntity<PlayerBioDto> getPlayerBio(@PathVariable Long id) {
        return playerService.getPlayerBioById(id)
                .map(ResponseEntity::ok)                   // Found — 200 with body
                .orElse(ResponseEntity.noContent().build()); // Not found — 204 No Content
    }
}
