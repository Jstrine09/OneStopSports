package com.onestopsports.service;

import com.onestopsports.dto.PlayerBioDto;
import com.onestopsports.dto.PlayerDto;
import com.onestopsports.model.Player;
import com.onestopsports.repository.PlayerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

// Handles business logic for Players.
// All player data comes from our database (seeded at startup from football-data.org).
// Bio enrichment (height, weight, college, draft info) is fetched live from balldontlie.io.
@Service
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final BallDontLieService ballDontLieService;

    public PlayerService(PlayerRepository playerRepository, BallDontLieService ballDontLieService) {
        this.playerRepository = playerRepository;
        this.ballDontLieService = ballDontLieService;
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

    // Looks up biographical data for a player from balldontlie.io.
    // Returns empty if the player doesn't exist, or if balldontlie has no record
    // (e.g. football/NFL players who aren't in the NBA database).
    // Called by GET /api/players/{id}/bio — used on PlayerDetailPage to enrich NBA profiles.
    public Optional<PlayerBioDto> getPlayerBioById(Long id) {
        // First confirm the player actually exists in our DB
        Player player = playerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found: " + id));

        // Search balldontlie by full name — will return empty for non-NBA players
        return ballDontLieService.searchPlayerByName(player.getName());
    }

    // Returns players whose name contains the query string (case-insensitive).
    // Capped at 10 results so the search results page stays readable.
    // Called by GET /api/search?q=...
    public List<PlayerDto> searchPlayers(String query) {
        return playerRepository.findByNameContainingIgnoreCase(query)
                .stream()
                .limit(10)
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
