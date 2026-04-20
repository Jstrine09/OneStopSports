package com.matchday.controller;

import com.matchday.dto.FavoritePlayerRequest;
import com.matchday.dto.FavoriteTeamRequest;
import com.matchday.dto.PlayerDto;
import com.matchday.dto.TeamDto;
import com.matchday.dto.UserDto;
import com.matchday.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<UserDto> getMe(Principal principal) {
        return ResponseEntity.ok(userService.getCurrentUser(principal.getName()));
    }

    @GetMapping("/favorites/teams")
    public ResponseEntity<List<TeamDto>> getFavoriteTeams(Principal principal) {
        return ResponseEntity.ok(userService.getFavoriteTeams(principal.getName()));
    }

    @PostMapping("/favorites/teams")
    public ResponseEntity<Void> addFavoriteTeam(@RequestBody FavoriteTeamRequest request,
                                                Principal principal) {
        userService.addFavoriteTeam(principal.getName(), request.teamId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/favorites/teams/{teamId}")
    public ResponseEntity<Void> removeFavoriteTeam(@PathVariable Long teamId, Principal principal) {
        userService.removeFavoriteTeam(principal.getName(), teamId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/favorites/players")
    public ResponseEntity<List<PlayerDto>> getFavoritePlayers(Principal principal) {
        return ResponseEntity.ok(userService.getFavoritePlayers(principal.getName()));
    }

    @PostMapping("/favorites/players")
    public ResponseEntity<Void> addFavoritePlayer(@RequestBody FavoritePlayerRequest request,
                                                  Principal principal) {
        userService.addFavoritePlayer(principal.getName(), request.playerId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/favorites/players/{playerId}")
    public ResponseEntity<Void> removeFavoritePlayer(@PathVariable Long playerId, Principal principal) {
        userService.removeFavoritePlayer(principal.getName(), playerId);
        return ResponseEntity.noContent().build();
    }
}
