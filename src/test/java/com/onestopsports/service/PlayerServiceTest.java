package com.onestopsports.service;

import com.onestopsports.dto.PlayerDto;
import com.onestopsports.model.League;
import com.onestopsports.model.Player;
import com.onestopsports.model.Sport;
import com.onestopsports.model.Team;
import com.onestopsports.repository.PlayerRepository;
import com.onestopsports.util.TextNormalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

// Pure unit tests for the three PlayerService methods NOT covered by
// PlayerServiceCareerStatsTest: resolvePhotoUrl (private, exercised through the public
// getPlayerById/toDto path), toDto's field mapping, and searchPlayers (normalize + cap at 10).
//
// Same style as PlayerServiceCareerStatsTest: plain Java entity builders stand in for the
// lazy DB chain, all five collaborators are Mockito mocks, no Spring context, no real DB, no HTTP.
@ExtendWith(MockitoExtension.class)
class PlayerServiceTest {

    @Mock private PlayerRepository   playerRepository;
    @Mock private BallDontLieService ballDontLieService;     // unused by these methods — keep happy
    @Mock private NbaApiService      nbaApiService;          // unused by these methods — keep happy
    @Mock private NflApiService      nflApiService;          // unused by these methods — keep happy
    @Mock private ApiFootballService apiFootballService;     // unused by these methods — keep happy

    @InjectMocks
    private PlayerService playerService;

    // ── Helper: build a Player with a full sport chain + optional photoUrl ────
    // Mirrors PlayerServiceCareerStatsTest.playerInSport but also lets a test control
    // photoUrl directly, since resolvePhotoUrl's Layer 1 (persisted value) is what we're testing here.
    private static Player playerWithPhoto(Long id, String sportSlug, String externalId,
                                           String photoUrl, String name, Long teamId) {
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
                .build();
        Team team = Team.builder()
                .id(teamId)
                .sport(sport)
                .name("Test Team")
                .build();
        team.addLeague(league);
        return Player.builder()
                .id(id)
                .team(team)
                .name(name)
                .position("Forward")
                .nationality("Norway")
                .dateOfBirth(LocalDate.of(2000, 7, 21))
                .jerseyNumber(9)
                .externalId(externalId)
                .photoUrl(photoUrl)
                .build();
    }

    // ── resolvePhotoUrl: Layer 1 — persisted photoUrl always wins ──────────────

    @Test
    void getPlayerById_persistedPhotoUrl_winsRegardlessOfSportOrExternalId() {
        // A player with a stored photoUrl should return exactly that URL, even though they
        // also have a basketball sport + externalId that COULD derive an ESPN CDN URL —
        // Layer 1 (persisted) must short-circuit before Layer 2 (derived) is even considered.
        Player haaland = playerWithPhoto(1L, "basketball", "1966",
                "https://example.com/custom-photo.png", "Erling Haaland", 100L);
        when(playerRepository.findById(1L)).thenReturn(Optional.of(haaland));

        PlayerDto dto = playerService.getPlayerById(1L);

        assertThat(dto.photoUrl()).isEqualTo("https://example.com/custom-photo.png");
    }

    // ── resolvePhotoUrl: Layer 2 — NBA CDN derivation ──────────────────────────

    @Test
    void getPlayerById_basketballWithExternalIdNoPhoto_derivesNbaCdnUrl() {
        // No persisted photoUrl, but a basketball player with an ESPN athlete ID —
        // the URL should be reconstructed from the known ESPN NBA headshot CDN pattern.
        Player lebron = playerWithPhoto(2L, "basketball", "1966", null, "LeBron James", 101L);
        when(playerRepository.findById(2L)).thenReturn(Optional.of(lebron));

        PlayerDto dto = playerService.getPlayerById(2L);

        assertThat(dto.photoUrl()).isEqualTo("https://a.espncdn.com/i/headshots/nba/players/full/1966.png");
    }

    // ── resolvePhotoUrl: Layer 2 — NFL CDN derivation ──────────────────────────

    @Test
    void getPlayerById_americanFootballWithExternalIdNoPhoto_derivesNflCdnUrl() {
        // Same idea as the NBA case, but for american-football → the NFL headshot CDN path.
        Player mahomes = playerWithPhoto(3L, "american-football", "3139477", null, "Patrick Mahomes", 102L);
        when(playerRepository.findById(3L)).thenReturn(Optional.of(mahomes));

        PlayerDto dto = playerService.getPlayerById(3L);

        assertThat(dto.photoUrl()).isEqualTo("https://a.espncdn.com/i/headshots/nfl/players/full/3139477.png");
    }

    // ── resolvePhotoUrl: Layer 3 — null when no photo can be resolved ─────────

    @Test
    void getPlayerById_noPhotoNoExternalId_returnsNullPhotoUrl() {
        // A football (soccer) player who hasn't had their stats page visited yet has neither
        // a persisted photoUrl nor an externalId — there's no CDN pattern for football, so
        // the frontend must fall back to initials (photoUrl == null).
        Player rookie = playerWithPhoto(4L, "football", null, null, "New Signing", 103L);
        when(playerRepository.findById(4L)).thenReturn(Optional.of(rookie));

        PlayerDto dto = playerService.getPlayerById(4L);

        assertThat(dto.photoUrl()).isNull();
    }

    @Test
    void getPlayerById_unsupportedSportWithExternalId_returnsNullPhotoUrl() {
        // Even WITH an externalId, a sport outside the NBA/NFL switch cases (e.g. football,
        // which is handled by Layer 1 only) must fall through to null rather than guessing a URL.
        Player oddball = playerWithPhoto(5L, "football", "12345", null, "Some Footballer", 104L);
        when(playerRepository.findById(5L)).thenReturn(Optional.of(oddball));

        PlayerDto dto = playerService.getPlayerById(5L);

        assertThat(dto.photoUrl()).isNull();
    }

    // ── toDto: full field mapping ───────────────────────────────────────────────

    @Test
    void getPlayerById_mapsAllNonPhotoFieldsAndTeamId() {
        // Confirms toDto correctly copies every scalar field from the entity onto the DTO,
        // plus the team's id (which requires a lazy load of the Team association).
        Player player = playerWithPhoto(6L, "basketball", "1966", null, "LeBron James", 200L);
        when(playerRepository.findById(6L)).thenReturn(Optional.of(player));

        PlayerDto dto = playerService.getPlayerById(6L);

        assertThat(dto.id()).isEqualTo(6L);
        assertThat(dto.name()).isEqualTo("LeBron James");
        assertThat(dto.position()).isEqualTo("Forward");
        assertThat(dto.nationality()).isEqualTo("Norway");
        assertThat(dto.dateOfBirth()).isEqualTo(LocalDate.of(2000, 7, 21));
        assertThat(dto.jerseyNumber()).isEqualTo(9);
        assertThat(dto.teamId()).isEqualTo(200L);
    }

    // ── searchPlayers: normalize + cap at 10 ────────────────────────────────────

    @Test
    void searchPlayers_normalizesQueryAndCapsResultsAtTen() {
        // GIVEN the repository would return more than 10 matches (e.g. 12 players named
        // similarly) — searchPlayers must trim to exactly 10 so the results page stays readable.
        // The query itself must be normalized (accent-stripped, lower-cased) before it's
        // passed to the repository, so "Dembele" can still match a stored "Dembélé".
        List<Player> twelvePlayers = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            twelvePlayers.add(playerWithPhoto((long) (300 + i), "football", null, null,
                    "Player " + i, 400L));
        }
        String query = "Dembélé";
        String expectedNormalized = TextNormalizer.normalize(query); // "dembele"
        when(playerRepository.findByNameNormalizedContaining(expectedNormalized))
                .thenReturn(twelvePlayers);

        List<PlayerDto> results = playerService.searchPlayers(query);

        assertThat(results).hasSize(10);
    }

    @Test
    void searchPlayers_noMatches_returnsEmptyList() {
        // A search with zero repository hits should just come back empty, not throw.
        when(playerRepository.findByNameNormalizedContaining(anyString())).thenReturn(List.of());

        List<PlayerDto> results = playerService.searchPlayers("nobody");

        assertThat(results).isEmpty();
    }
}
