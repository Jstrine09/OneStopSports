package com.onestopsports.controller;

import com.onestopsports.dto.FavoritePlayerRequest;
import com.onestopsports.dto.FavoriteTeamRequest;
import com.onestopsports.dto.PlayerDto;
import com.onestopsports.dto.TeamDto;
import com.onestopsports.dto.UserDto;
import com.onestopsports.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

// Handles all user-specific endpoints — profile and favourites.
// Every endpoint here requires authentication (enforced by SecurityConfig).
// Principal is Spring's way of giving us the currently logged-in user's username.
@RestController
@RequestMapping("/api/users/me") // All endpoints are under /api/users/me (always refers to the logged-in user)
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // GET /api/users/me
    // Returns the logged-in user's profile (username, email, createdAt).
    // principal.getName() gives us the username extracted from their JWT token.
    @GetMapping
    public ResponseEntity<UserDto> getMe(Principal principal) {
        return ResponseEntity.ok(userService.getCurrentUser(principal.getName()));
    }

    // GET /api/users/me/favorites/teams
    // Returns all teams the logged-in user has favourited.
    @GetMapping("/favorites/teams")
    public ResponseEntity<List<TeamDto>> getFavoriteTeams(Principal principal) {
        return ResponseEntity.ok(userService.getFavoriteTeams(principal.getName()));
    }

    // POST /api/users/me/favorites/teams
    // Adds a team to the user's favourites. Returns 201 Created.
    // Body: { "teamId": 5 }
    @PostMapping("/favorites/teams")
    public ResponseEntity<Void> addFavoriteTeam(@RequestBody FavoriteTeamRequest request, Principal principal) {
        userService.addFavoriteTeam(principal.getName(), request.teamId());
        return ResponseEntity.status(HttpStatus.CREATED).build(); // No body — just the 201 status
    }

    // DELETE /api/users/me/favorites/teams/{teamId}
    // Removes a team from the user's favourites. Returns 204 No Content.
    @DeleteMapping("/favorites/teams/{teamId}")
    public ResponseEntity<Void> removeFavoriteTeam(@PathVariable Long teamId, Principal principal) {
        userService.removeFavoriteTeam(principal.getName(), teamId);
        return ResponseEntity.noContent().build(); // 204 — success but nothing to return
    }

    // GET /api/users/me/favorites/players
    // Returns all players the logged-in user has favourited.
    @GetMapping("/favorites/players")
    public ResponseEntity<List<PlayerDto>> getFavoritePlayers(Principal principal) {
        return ResponseEntity.ok(userService.getFavoritePlayers(principal.getName()));
    }

    // POST /api/users/me/favorites/players
    // Adds a player to the user's favourites. Returns 201 Created.
    // Body: { "playerId": 42 }
    @PostMapping("/favorites/players")
    public ResponseEntity<Void> addFavoritePlayer(@RequestBody FavoritePlayerRequest request, Principal principal) {
        userService.addFavoritePlayer(principal.getName(), request.playerId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // DELETE /api/users/me/favorites/players/{playerId}
    // Removes a player from the user's favourites. Returns 204 No Content.
    @DeleteMapping("/favorites/players/{playerId}")
    public ResponseEntity<Void> removeFavoritePlayer(@PathVariable Long playerId, Principal principal) {
        userService.removeFavoritePlayer(principal.getName(), playerId);
        return ResponseEntity.noContent().build();
    }
}
