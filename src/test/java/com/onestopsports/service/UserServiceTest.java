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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pure unit tests for UserService — no Spring context, no database, no HTTP.
// UserService has SEVEN constructor dependencies (two repositories for the user's own
// data, two "favorite" join-table repositories, and two sibling services it reuses for
// DTO mapping) — every one of them is replaced with a Mockito mock below, exactly like
// MatchServiceTest does for MatchService.
//
// What's being pinned here:
//   1. findUser's 404 guard (an unknown username throws ResponseStatusException) — this
//      is a private helper reused by every public method, so we only need to prove it
//      once via getCurrentUser and trust the other methods share the same code path.
//   2. The "skip if already favourited" guard — addFavoriteTeam/addFavoritePlayer must
//      NOT touch the team/player repository or save anything if the favourite already exists.
//   3. The "team/player not found" 404 guard on the happy path when it's a genuinely new favourite.
//   4. Happy-path saves — exactly one row is written.
//   5. Delegation to TeamService/PlayerService for DTO mapping, and delegation of the
//      remove methods to the repository's delete-by-user-and-target method.
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    // All seven UserService dependencies are mocked so no real DB or Spring bean is touched
    @Mock private UserRepository userRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private FavoriteTeamRepository favoriteTeamRepository;
    @Mock private FavoritePlayerRepository favoritePlayerRepository;
    @Mock private TeamService teamService;
    @Mock private PlayerService playerService;

    // @InjectMocks builds a real UserService and wires all seven mocks above into its
    // constructor (Mockito matches by type, so declaration order above doesn't matter)
    @InjectMocks
    private UserService userService;

    // A fixed fixture user reused across the happy-path tests
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2025, 1, 1, 12, 0);

    private static UserAccount fixtureUser() {
        return UserAccount.builder()
                .id(1L)
                .username("jamesstrine")
                .email("james@example.com")
                .passwordHash("irrelevant-hash")
                .createdAt(CREATED_AT)
                .build();
    }

    // ── getCurrentUser ────────────────────────────────────────────────────────

    @Test
    void getCurrentUser_knownUsername_returnsUserDtoWithProfileFields() {
        // GIVEN: the repository knows about this username
        when(userRepository.findByUsername("jamesstrine")).thenReturn(Optional.of(fixtureUser()));

        // WHEN
        UserDto result = userService.getCurrentUser("jamesstrine");

        // THEN: the DTO carries the id/username/email straight through from the entity
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.username()).isEqualTo("jamesstrine");
        assertThat(result.email()).isEqualTo("james@example.com");
        assertThat(result.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void getCurrentUser_unknownUsername_throws404WithUserNotFoundMessage() {
        // GIVEN: no user with this username exists
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        // WHEN / THEN: findUser's guard clause throws a 404, not an NPE
        assertThatThrownBy(() -> userService.getCurrentUser("ghost"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("User not found");
    }

    // ── addFavoriteTeam ───────────────────────────────────────────────────────

    @Test
    void addFavoriteTeam_alreadyFavourited_returnsWithoutTouchingTeamOrSaving() {
        // GIVEN: the user already has this team favourited
        when(userRepository.findByUsername("jamesstrine")).thenReturn(Optional.of(fixtureUser()));
        when(favoriteTeamRepository.existsByUserIdAndTeamId(1L, 42L)).thenReturn(true);

        // WHEN
        userService.addFavoriteTeam("jamesstrine", 42L);

        // THEN: the guard bails out early — no lookup, no save, no duplicate row
        verify(teamRepository, never()).findById(anyLong());
        verify(favoriteTeamRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void addFavoriteTeam_missingTeam_throws404AndDoesNotSave() {
        // GIVEN: not already favourited, but the team doesn't exist in the DB
        when(userRepository.findByUsername("jamesstrine")).thenReturn(Optional.of(fixtureUser()));
        when(favoriteTeamRepository.existsByUserIdAndTeamId(1L, 999L)).thenReturn(false);
        when(teamRepository.findById(999L)).thenReturn(Optional.empty());

        // WHEN / THEN
        assertThatThrownBy(() -> userService.addFavoriteTeam("jamesstrine", 999L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Team not found");
        verify(favoriteTeamRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void addFavoriteTeam_newFavourite_savesExactlyOneFavoriteTeamRow() {
        // GIVEN: a real team the user hasn't favourited yet
        Team team = Team.builder().id(42L).name("Manchester City FC").build();
        when(userRepository.findByUsername("jamesstrine")).thenReturn(Optional.of(fixtureUser()));
        when(favoriteTeamRepository.existsByUserIdAndTeamId(1L, 42L)).thenReturn(false);
        when(teamRepository.findById(42L)).thenReturn(Optional.of(team));

        // WHEN
        userService.addFavoriteTeam("jamesstrine", 42L);

        // THEN: exactly one FavoriteTeam row gets written
        verify(favoriteTeamRepository, times(1)).save(org.mockito.ArgumentMatchers.any(FavoriteTeam.class));
    }

    // ── addFavoritePlayer (symmetric guard) ──────────────────────────────────

    @Test
    void addFavoritePlayer_missingPlayer_throws404AndDoesNotSave() {
        // Same shape as addFavoriteTeam's 404 case, proving the player path guards identically
        when(userRepository.findByUsername("jamesstrine")).thenReturn(Optional.of(fixtureUser()));
        when(favoritePlayerRepository.existsByUserIdAndPlayerId(1L, 77L)).thenReturn(false);
        when(playerRepository.findById(77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.addFavoritePlayer("jamesstrine", 77L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Player not found");
        verify(favoritePlayerRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void addFavoritePlayer_alreadyFavourited_returnsWithoutTouchingPlayerOrSaving() {
        // Same shape as addFavoriteTeam's skip-if-exists case, proving the player path
        // shares the same "don't duplicate" guard.
        when(userRepository.findByUsername("jamesstrine")).thenReturn(Optional.of(fixtureUser()));
        when(favoritePlayerRepository.existsByUserIdAndPlayerId(1L, 77L)).thenReturn(true);

        userService.addFavoritePlayer("jamesstrine", 77L);

        verify(playerRepository, never()).findById(anyLong());
        verify(favoritePlayerRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    // ── getFavoriteTeams / getFavoritePlayers — DTO-mapping delegation ───────

    @Test
    void getFavoriteTeams_mapsEachRowThroughTeamServiceToDto() {
        // GIVEN: one favourite-team row, and TeamService.toDto stubbed to return a known DTO
        Team team = Team.builder().id(42L).name("Manchester City FC").build();
        FavoriteTeam favorite = FavoriteTeam.builder().id(1L).user(fixtureUser()).team(team).build();
        TeamDto expectedDto = new TeamDto(42L, "Manchester City FC", "Man City", null, null, null, null);

        when(userRepository.findByUsername("jamesstrine")).thenReturn(Optional.of(fixtureUser()));
        when(favoriteTeamRepository.findByUserId(1L)).thenReturn(List.of(favorite));
        when(teamService.toDto(team)).thenReturn(expectedDto);

        // WHEN
        List<TeamDto> result = userService.getFavoriteTeams("jamesstrine");

        // THEN: the mocked TeamService.toDto output flows straight through — proves
        // UserService delegates mapping rather than building the DTO itself
        assertThat(result).containsExactly(expectedDto);
    }

    @Test
    void getFavoritePlayers_mapsEachRowThroughPlayerServiceToDto() {
        // Symmetric delegation test for the player-favourites path
        Player player = Player.builder().id(77L).name("Erling Haaland").build();
        FavoritePlayer favorite = FavoritePlayer.builder().id(1L).user(fixtureUser()).player(player).build();
        PlayerDto expectedDto = new PlayerDto(77L, "Erling Haaland", "Forward", "Norway", null, 9, null, 42L);

        when(userRepository.findByUsername("jamesstrine")).thenReturn(Optional.of(fixtureUser()));
        when(favoritePlayerRepository.findByUserId(1L)).thenReturn(List.of(favorite));
        when(playerService.toDto(player)).thenReturn(expectedDto);

        List<PlayerDto> result = userService.getFavoritePlayers("jamesstrine");

        assertThat(result).containsExactly(expectedDto);
    }

    // ── removeFavoriteTeam / removeFavoritePlayer — delegation ───────────────

    @Test
    void removeFavoriteTeam_delegatesToRepositoryDeleteByUserIdAndTeamId() {
        // GIVEN: a known user
        when(userRepository.findByUsername("jamesstrine")).thenReturn(Optional.of(fixtureUser()));

        // WHEN
        userService.removeFavoriteTeam("jamesstrine", 42L);

        // THEN: the resolved user id (not the username) and the team id are passed straight through
        verify(favoriteTeamRepository).deleteByUserIdAndTeamId(1L, 42L);
    }

    @Test
    void removeFavoritePlayer_delegatesToRepositoryDeleteByUserIdAndPlayerId() {
        // Symmetric delegation test for removing a favourite player
        when(userRepository.findByUsername("jamesstrine")).thenReturn(Optional.of(fixtureUser()));

        userService.removeFavoritePlayer("jamesstrine", 77L);

        verify(favoritePlayerRepository).deleteByUserIdAndPlayerId(1L, 77L);
    }
}
