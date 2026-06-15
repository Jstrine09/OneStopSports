package com.onestopsports.service;

import com.onestopsports.dto.PlayerCareerStatsDto;
import com.onestopsports.model.League;
import com.onestopsports.model.Player;
import com.onestopsports.model.Sport;
import com.onestopsports.model.Team;
import com.onestopsports.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pure unit tests for PlayerService.getPlayerCareerStats — the multi-sport router.
//
// The routing is the interesting bit: based on the player's sport slug we call
// NbaApiService, NflApiService, or ApiFootballService. None of the upstream services
// should ever be called for the wrong sport (a basketball player must NOT hit the NFL API).
//
// All upstream API calls are mocked — no real HTTP, no Spring context, no Hibernate.
// We just build plain Java entities with the builder to stand in for the lazy DB chain.
@ExtendWith(MockitoExtension.class)
class PlayerServiceCareerStatsTest {

    @Mock private PlayerRepository   playerRepository;
    @Mock private BallDontLieService ballDontLieService;     // unused but injected — keep happy
    @Mock private NbaApiService      nbaApiService;
    @Mock private NflApiService      nflApiService;
    @Mock private ApiFootballService apiFootballService;

    @InjectMocks
    private PlayerService playerService;

    // ── Helper: build a Player with its full sport chain populated ────────────
    // Mirrors the DB chain the service walks in prod: player → team → sport for routing,
    // and player → team → primary league for the football API-Football lookup. Sport now
    // lives directly on the Team; the league is attached via the team↔league association.
    private static Player playerInSport(Long id, String sportSlug, String externalId,
                                        String playerName, Integer leagueExternalId) {
        Sport sport = Sport.builder()
                .id(switch (sportSlug) {
                    case "basketball" -> 2L;
                    case "american-football" -> 3L;
                    default -> 1L;
                })
                .slug(sportSlug)
                .name(sportSlug)
                .build();
        League league = League.builder()
                .id(10L)
                .sport(sport)
                .name("Test League")
                .externalId(leagueExternalId)
                .build();
        Team team = Team.builder()
                .id(100L)
                .sport(sport)
                .name("Test Team")
                .build();
        team.addLeague(league); // primary league = this one (single membership)
        return Player.builder()
                .id(id)
                .team(team)
                .name(playerName)
                .externalId(externalId)
                .build();
    }

    // Minimal stats DTO returned by the mocked upstream services.
    // competition is null — NBA/NFL test cases mirror what those services would produce.
    private static final PlayerCareerStatsDto DUMMY_STATS = new PlayerCareerStatsDto(
            "any",
            List.of(new PlayerCareerStatsDto.StatCategory(
                    "averages", "Per Game",
                    List.of("GP", "PTS"),
                    List.of(new PlayerCareerStatsDto.SeasonRow("2024-25", "BOS", null, List.of("82", "27.5"))),
                    new PlayerCareerStatsDto.SeasonRow(null, null, null, List.of("1000", "26.8"))
            )));

    // ── 404 path ──────────────────────────────────────────────────────────────

    @Test
    void getPlayerCareerStats_unknownPlayerId_throws404() {
        // A request for a player that isn't in our DB must return 404 — not 500, not
        // an attempt to call an upstream API with a null externalId.
        when(playerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> playerService.getPlayerCareerStats(999L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Player not found");

        verify(nbaApiService, never()).fetchCareerStats(anyString());
        verify(nflApiService, never()).fetchCareerStats(anyString());
        verify(apiFootballService, never()).fetchPlayerStats(anyInt(), anyInt());
    }

    // ── NBA routing ───────────────────────────────────────────────────────────

    @Test
    void getPlayerCareerStats_basketballWithExternalId_routesToNba() {
        // GIVEN an NBA player with a stored ESPN athlete ID
        Player lebron = playerInSport(1L, "basketball", "1966", "LeBron James", null);
        when(playerRepository.findById(1L)).thenReturn(Optional.of(lebron));
        when(nbaApiService.fetchCareerStats("1966")).thenReturn(DUMMY_STATS);

        Optional<PlayerCareerStatsDto> result = playerService.getPlayerCareerStats(1L);

        // THEN ESPN NBA is called with the right ID, others are not touched
        assertThat(result).contains(DUMMY_STATS);
        verify(nbaApiService).fetchCareerStats("1966");
        verify(nflApiService, never()).fetchCareerStats(anyString());
        verify(apiFootballService, never()).fetchPlayerStats(anyInt(), anyInt());
    }

    @Test
    void getPlayerCareerStats_basketballWithoutExternalId_returnsEmpty() {
        // Pre-V6 NBA player that hasn't been re-seeded — no externalId, can't query ESPN.
        // Should fail gracefully (empty Optional → 204) rather than call ESPN with null.
        Player oldRow = playerInSport(2L, "basketball", null, "Forgotten Player", null);
        when(playerRepository.findById(2L)).thenReturn(Optional.of(oldRow));

        Optional<PlayerCareerStatsDto> result = playerService.getPlayerCareerStats(2L);

        assertThat(result).isEmpty();
        verify(nbaApiService, never()).fetchCareerStats(any());
    }

    // ── NFL routing ───────────────────────────────────────────────────────────

    @Test
    void getPlayerCareerStats_americanFootballWithExternalId_routesToNfl() {
        Player mahomes = playerInSport(3L, "american-football", "3139477", "Patrick Mahomes", null);
        when(playerRepository.findById(3L)).thenReturn(Optional.of(mahomes));
        when(nflApiService.fetchCareerStats("3139477")).thenReturn(DUMMY_STATS);

        Optional<PlayerCareerStatsDto> result = playerService.getPlayerCareerStats(3L);

        assertThat(result).contains(DUMMY_STATS);
        verify(nflApiService).fetchCareerStats("3139477");
        verify(nbaApiService, never()).fetchCareerStats(anyString());
        verify(apiFootballService, never()).fetchPlayerStats(anyInt(), anyInt());
    }

    // ── Football routing ──────────────────────────────────────────────────────

    @Test
    void getPlayerCareerStats_footballWithExternalId_skipsSearch() {
        // Football player whose API-SPORTS ID is already cached — must NOT call searchPlayerId
        // (would waste one of our 100 daily API calls).
        Player haaland = playerInSport(4L, "football", "1100", "Erling Haaland", 2021);
        when(playerRepository.findById(4L)).thenReturn(Optional.of(haaland));
        when(apiFootballService.currentSeason()).thenReturn(2025);
        when(apiFootballService.fetchPlayerStats(1100, 2025)).thenReturn(DUMMY_STATS);

        Optional<PlayerCareerStatsDto> result = playerService.getPlayerCareerStats(4L);

        assertThat(result).contains(DUMMY_STATS);
        verify(apiFootballService).fetchPlayerStats(1100, 2025);
        verify(apiFootballService, never()).searchPlayerId(anyString(), anyInt(), anyInt());
        // No DB write needed — externalId was already populated
        verify(playerRepository, never()).save(any(Player.class));
    }

    @Test
    void getPlayerCareerStats_footballWithoutExternalId_searchesAndPersistsId() {
        // First-ever stats request for this football player.
        // Flow: search by name + league → save the ID → fetch the stats.
        Player rookie = playerInSport(5L, "football", null, "New Signing", 2021);
        when(playerRepository.findById(5L)).thenReturn(Optional.of(rookie));
        when(apiFootballService.currentSeason()).thenReturn(2025);
        when(apiFootballService.mapLeagueId(2021)).thenReturn(39); // PL → API-SPORTS league 39
        when(apiFootballService.searchPlayerId("New Signing", 39, 2025))
                .thenReturn(Optional.of(54321));
        when(apiFootballService.fetchPlayerStats(54321, 2025)).thenReturn(DUMMY_STATS);

        Optional<PlayerCareerStatsDto> result = playerService.getPlayerCareerStats(5L);

        // THEN we get stats AND the player row gets the externalId stamped on it
        assertThat(result).contains(DUMMY_STATS);
        verify(apiFootballService).searchPlayerId("New Signing", 39, 2025);
        verify(apiFootballService).fetchPlayerStats(54321, 2025);
        verify(playerRepository).save(rookie);
        assertThat(rookie.getExternalId()).isEqualTo("54321"); // ID was actually written
    }

    @Test
    void getPlayerCareerStats_footballSearchMisses_returnsEmpty() {
        // Search returned no match (player not in that league this season, or name mismatch).
        // We must not call fetchPlayerStats and must not save anything.
        Player ghost = playerInSport(6L, "football", null, "Ghost Player", 2021);
        when(playerRepository.findById(6L)).thenReturn(Optional.of(ghost));
        when(apiFootballService.currentSeason()).thenReturn(2025);
        when(apiFootballService.mapLeagueId(2021)).thenReturn(39);
        when(apiFootballService.searchPlayerId(eq("Ghost Player"), eq(39), eq(2025)))
                .thenReturn(Optional.empty());

        Optional<PlayerCareerStatsDto> result = playerService.getPlayerCareerStats(6L);

        assertThat(result).isEmpty();
        verify(apiFootballService, never()).fetchPlayerStats(anyInt(), anyInt());
        verify(playerRepository, never()).save(any(Player.class));
    }

    @Test
    void getPlayerCareerStats_footballUnknownLeague_returnsEmpty() {
        // Player's league isn't in our football-data → API-SPORTS mapping
        // (e.g. an FA Cup or domestic friendly we haven't onboarded).
        // We should bail before any HTTP call — no search, no fetch, no save.
        Player cupPlayer = playerInSport(7L, "football", null, "Cup Player", 9999);
        when(playerRepository.findById(7L)).thenReturn(Optional.of(cupPlayer));
        when(apiFootballService.currentSeason()).thenReturn(2025);
        when(apiFootballService.mapLeagueId(9999)).thenReturn(null);

        Optional<PlayerCareerStatsDto> result = playerService.getPlayerCareerStats(7L);

        assertThat(result).isEmpty();
        verify(apiFootballService, never()).searchPlayerId(anyString(), anyInt(), anyInt());
        verify(apiFootballService, never()).fetchPlayerStats(anyInt(), anyInt());
        verify(playerRepository, never()).save(any(Player.class));
    }

    // ── Unsupported sport ─────────────────────────────────────────────────────

    @Test
    void getPlayerCareerStats_unknownSport_returnsEmpty() {
        // Future-proofing: if we ever add a sport without a stats integration,
        // the router should fall through cleanly rather than throwing.
        Player tennis = playerInSport(8L, "tennis", "anything", "Roger Federer", null);
        when(playerRepository.findById(8L)).thenReturn(Optional.of(tennis));

        Optional<PlayerCareerStatsDto> result = playerService.getPlayerCareerStats(8L);

        assertThat(result).isEmpty();
        verify(nbaApiService, never()).fetchCareerStats(anyString());
        verify(nflApiService, never()).fetchCareerStats(anyString());
        verify(apiFootballService, never()).fetchPlayerStats(anyInt(), anyInt());
    }
}
