package com.matchday.controller;

import com.matchday.dto.PlayerDto;
import com.matchday.dto.TeamDto;
import com.matchday.service.PlayerService;
import com.matchday.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;
    private final PlayerService playerService;

    @GetMapping("/{id}")
    public ResponseEntity<TeamDto> getTeam(@PathVariable Long id) {
        return ResponseEntity.ok(teamService.getTeamById(id));
    }

    @GetMapping("/{id}/players")
    public ResponseEntity<List<PlayerDto>> getPlayers(@PathVariable Long id) {
        return ResponseEntity.ok(playerService.getPlayersByTeam(id));
    }
}
