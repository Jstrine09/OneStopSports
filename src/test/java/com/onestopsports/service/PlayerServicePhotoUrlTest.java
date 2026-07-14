package com.onestopsports.service;

import com.onestopsports.dto.PlayerDto;
import com.onestopsports.model.Player;
import com.onestopsports.model.Sport;
import com.onestopsports.model.Team;
import com.onestopsports.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

// Pure unit tests for the headshot URL that PlayerService.toDto exposes as PlayerDto.photoUrl.
//
// resolvePhotoUrl is private, so we drive it the same way real requests do: through
// getPlayerById, which maps the entity via toDto. That keeps the test black-box — we assert
// on the DTO the controller would return, not on an internal method.
//
// The interesting bit is the per-sport derivation: NBA/NFL headshots come from ESPN's CDN,
// football headshots from the API-SPORTS media CDN — each keyed off the player's externalId.
// Same mocking style as PlayerServiceCareerStatsTest: plain builder entities, no Spring, no DB.
@ExtendWith(MockitoExtension.class)
class PlayerServicePhotoUrlTest {

    @Mock private PlayerRepository   playerRepository;
    @Mock private BallDontLieService ballDontLieService;     // unused but injected — keep happy
    @Mock private NbaApiService      nbaApiService;
    @Mock private NflApiService      nflApiService;
    @Mock private ApiFootballService apiFootballService;

    @InjectMocks
    private PlayerService playerService;

    // ── Helper: build a Player wired to a Team that carries the given sport ─────
    // toDto walks player → team (for the team id) → sport (for the photo routing), so both
    // links must be present. photoUrl is left null unless a test sets it explicitly.
    private static Player playerInSport(Long id, String sportSlug, String externalId) {
        Sport sport = Sport.builder()
                .id(1L)
                .slug(sportSlug)
                .name(sportSlug)
                .build();
        Team team = Team.builder()
                .id(100L)
                .sport(sport)
                .name("Test Team")
                .build();
        return Player.builder()
                .id(id)
                .team(team)
                .name("Test Player")
                .externalId(externalId)
                .build();
    }

    // Convenience: run a player through the real getPlayerById path and hand back its DTO.
    private PlayerDto dtoFor(Player player) {
        when(playerRepository.findById(player.getId())).thenReturn(Optional.of(player));
        return playerService.getPlayerById(player.getId());
    }

    // ── Football (the finding's focus) ─────────────────────────────────────────

    @Test
    void footballPlayerWithExternalId_derivesApiSportsHeadshot() {
        // A football player's externalId is the api-sports.io player ID (persisted after the
        // first career-stats lookup). We derive the media-CDN headshot straight from it.
        Player saka = playerInSport(1L, "football", "1460");

        PlayerDto dto = dtoFor(saka);

        assertThat(dto.photoUrl())
                .isEqualTo("https://media.api-sports.io/football/players/1460.png");
    }

    @Test
    void footballPlayerWithoutExternalId_hasNoPhoto() {
        // Footballers whose stats page has never been opened have no api-sports ID yet, so
        // there's nothing to derive — the DTO returns null and the frontend shows initials.
        Player unseeded = playerInSport(2L, "football", null);

        PlayerDto dto = dtoFor(unseeded);

        assertThat(dto.photoUrl()).isNull();
    }

    // ── NBA / NFL (existing behaviour — guard against regressions) ──────────────

    @Test
    void basketballPlayer_derivesEspnNbaHeadshot() {
        Player lebron = playerInSport(3L, "basketball", "1966");

        PlayerDto dto = dtoFor(lebron);

        assertThat(dto.photoUrl())
                .isEqualTo("https://a.espncdn.com/i/headshots/nba/players/full/1966.png");
    }

    @Test
    void americanFootballPlayer_derivesEspnNflHeadshot() {
        Player mahomes = playerInSport(4L, "american-football", "3139477");

        PlayerDto dto = dtoFor(mahomes);

        assertThat(dto.photoUrl())
                .isEqualTo("https://a.espncdn.com/i/headshots/nfl/players/full/3139477.png");
    }

    // ── Layer 1 + fall-through ──────────────────────────────────────────────────

    @Test
    void persistedPhotoUrl_winsOverDerivedUrl() {
        // If a row already carries an explicit photoUrl, it takes precedence over any
        // sport-derived CDN URL.
        Player player = playerInSport(5L, "football", "1460");
        player.setPhotoUrl("https://example.com/custom.png");

        PlayerDto dto = dtoFor(player);

        assertThat(dto.photoUrl()).isEqualTo("https://example.com/custom.png");
    }

    @Test
    void unsupportedSport_hasNoPhoto() {
        // A sport we have no headshot source for falls through to null — even with an
        // externalId — rather than fabricating a bad URL.
        Player tennis = playerInSport(6L, "tennis", "anything");

        PlayerDto dto = dtoFor(tennis);

        assertThat(dto.photoUrl()).isNull();
    }
}
