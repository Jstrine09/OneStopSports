package com.onestopsports.service;

import com.onestopsports.dto.PlayerDto;
import com.onestopsports.model.Player;
import com.onestopsports.repository.PlayerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

// Handles business logic for Players.
// All player data comes from our database (seeded at startup from football-data.org).
@Service
public class PlayerService {

    private final PlayerRepository playerRepository;

    public PlayerService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    // Returns a single player by their database ID, or throws 404 if not found.
    // Called by GET /api/players/{id} — used on the PlayerDetailPage when navigating directly by URL.
    public PlayerDto getPlayerById(Long id) {
        return playerRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found: " + id));
    }

    // Returns all players on a given team.
    // Called by GET /api/teams/{id}/players — used to display the squad roster on TeamDetailPage.
    public List<PlayerDto> getPlayersByTeam(Long teamId) {
        return playerRepository.findByTeamId(teamId).stream()
                .map(this::toDto)
                .toList();
    }

    // Package-private so UserService can reuse this converter for favourite player data.
    PlayerDto toDto(Player player) {
        return new PlayerDto(
                player.getId(),
                player.getName(),
                player.getPosition(),
                player.getNationality(),
                player.getDateOfBirth(),
                player.getJerseyNumber(),
                player.getPhotoUrl(),
                player.getTeam().getId()); // Triggers a lazy load of the Team — expected here
    }
}
