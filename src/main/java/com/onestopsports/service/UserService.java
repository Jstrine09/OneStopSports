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

@Service
public class UserService {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final FavoriteTeamRepository favoriteTeamRepository;
    private final FavoritePlayerRepository favoritePlayerRepository;
    private final TeamService teamService;
    private final PlayerService playerService;

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

    public UserDto getCurrentUser(String username) {
        UserAccount user = findUser(username);
        return new UserDto(user.getId(), user.getUsername(), user.getEmail(), user.getAvatarUrl(), user.getCreatedAt());
    }

    public List<TeamDto> getFavoriteTeams(String username) {
        UserAccount user = findUser(username);
        return favoriteTeamRepository.findByUserId(user.getId()).stream()
                .map(FavoriteTeam::getTeam)
                .map(teamService::toDto)
                .toList();
    }

    public void addFavoriteTeam(String username, Long teamId) {
        UserAccount user = findUser(username);
        if (favoriteTeamRepository.existsByUserIdAndTeamId(user.getId(), teamId)) return;
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found: " + teamId));
        favoriteTeamRepository.save(FavoriteTeam.builder().user(user).team(team).build());
    }

    public void removeFavoriteTeam(String username, Long teamId) {
        UserAccount user = findUser(username);
        favoriteTeamRepository.deleteByUserIdAndTeamId(user.getId(), teamId);
    }

    public List<PlayerDto> getFavoritePlayers(String username) {
        UserAccount user = findUser(username);
        return favoritePlayerRepository.findByUserId(user.getId()).stream()
                .map(FavoritePlayer::getPlayer)
                .map(playerService::toDto)
                .toList();
    }

    public void addFavoritePlayer(String username, Long playerId) {
        UserAccount user = findUser(username);
        if (favoritePlayerRepository.existsByUserIdAndPlayerId(user.getId(), playerId)) return;
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found: " + playerId));
        favoritePlayerRepository.save(FavoritePlayer.builder().user(user).player(player).build());
    }

    public void removeFavoritePlayer(String username, Long playerId) {
        UserAccount user = findUser(username);
        favoritePlayerRepository.deleteByUserIdAndPlayerId(user.getId(), playerId);
    }

    private UserAccount findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + username));
    }
}
