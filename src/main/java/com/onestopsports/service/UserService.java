package com.onestopsports.service;

import com.onestopsports.dto.PlayerDto;
import com.onestopsports.dto.TeamDto;
import com.onestopsports.dto.UserDto;
import com.onestopsports.model.FavoritePlayer;
import com.onestopsports.model.FavoriteTeam;
import com.onestopsports.model.Player;
import com.onestopsports.model.Team;
import com.onestopsports.model.UserAccount;
import com.onestopsports.repository.FavoritePlayerRepository;
import com.onestopsports.repository.FavoriteTeamRepository;
import com.onestopsports.repository.PlayerRepository;
import com.onestopsports.repository.TeamRepository;
import com.onestopsports.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

// Handles all user-specific operations: getting profile data and managing favourites.
// Controllers pass in the username (from the JWT token) and this service does the rest.
@Service
public class UserService {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final FavoriteTeamRepository favoriteTeamRepository;
    private final FavoritePlayerRepository favoritePlayerRepository;
    private final TeamService teamService;     // Reused to convert Team entities to TeamDtos
    private final PlayerService playerService; // Reused to convert Player entities to PlayerDtos

    public UserService(UserRepository userRepository,
                       TeamRepository teamRepository,
                       PlayerRepository playerRepository,
                       FavoriteTeamRepository favoriteTeamRepository,
                       FavoritePlayerRepository favoritePlayerRepository,
                       TeamService teamService,
                       PlayerService playerService) {
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
        this.playerRepository = playerRepository;
        this.favoriteTeamRepository = favoriteTeamRepository;
        this.favoritePlayerRepository = favoritePlayerRepository;
        this.teamService = teamService;
        this.playerService = playerService;
    }

    // Returns the logged-in user's profile data.
    // Called by GET /api/users/me
    public UserDto getCurrentUser(String username) {
        UserAccount user = findUser(username);
        return new UserDto(user.getId(), user.getUsername(), user.getEmail(), user.getAvatarUrl(), user.getCreatedAt());
    }

    // Returns all teams the user has favourited.
    // Fetches all FavoriteTeam rows for this user, then extracts the Team from each one.
    public List<TeamDto> getFavoriteTeams(String username) {
        UserAccount user = findUser(username);
        return favoriteTeamRepository.findByUserId(user.getId()).stream()
                .map(FavoriteTeam::getTeam)       // Get the Team object from each FavoriteTeam row
                .map(teamService::toDto)           // Convert each Team entity to a TeamDto
                .toList();
    }

    // Adds a team to the user's favourites.
    // Does nothing if it's already favourited (no duplicate check needed — just silently returns).
    public void addFavoriteTeam(String username, Long teamId) {
        UserAccount user = findUser(username);

        // Skip if already favourited to avoid a DB unique constraint error
        if (favoriteTeamRepository.existsByUserIdAndTeamId(user.getId(), teamId)) return;

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found: " + teamId));

        // Save a new row linking this user to this team
        favoriteTeamRepository.save(FavoriteTeam.builder().user(user).team(team).build());
    }

    // Removes a team from the user's favourites.
    public void removeFavoriteTeam(String username, Long teamId) {
        UserAccount user = findUser(username);
        favoriteTeamRepository.deleteByUserIdAndTeamId(user.getId(), teamId);
    }

    // Returns all players the user has favourited — same pattern as getFavoriteTeams.
    public List<PlayerDto> getFavoritePlayers(String username) {
        UserAccount user = findUser(username);
        return favoritePlayerRepository.findByUserId(user.getId()).stream()
                .map(FavoritePlayer::getPlayer)   // Get the Player object from each FavoritePlayer row
                .map(playerService::toDto)         // Convert each Player entity to a PlayerDto
                .toList();
    }

    // Adds a player to the user's favourites.
    public void addFavoritePlayer(String username, Long playerId) {
        UserAccount user = findUser(username);

        if (favoritePlayerRepository.existsByUserIdAndPlayerId(user.getId(), playerId)) return;

        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found: " + playerId));

        favoritePlayerRepository.save(FavoritePlayer.builder().user(user).player(player).build());
    }

    // Removes a player from the user's favourites.
    public void removeFavoritePlayer(String username, Long playerId) {
        UserAccount user = findUser(username);
        favoritePlayerRepository.deleteByUserIdAndPlayerId(user.getId(), playerId);
    }

    // Private helper — loads a UserAccount by username or throws 404.
    // Used by every method in this service so we don't repeat the lookup logic everywhere.
    private UserAccount findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + username));
    }
}
