package com.onestopsports.controller;

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
}
