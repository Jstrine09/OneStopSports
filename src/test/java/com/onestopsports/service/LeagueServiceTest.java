package com.onestopsports.service;

import com.onestopsports.dto.LeagueDto;
import com.onestopsports.dto.StandingsEntryDto;
import com.onestopsports.dto.TeamDto;
import com.onestopsports.model.League;
import com.onestopsports.model.Sport;
import com.onestopsports.repository.LeagueRepository;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pure unit tests for LeagueService — no Spring context, no database, no HTTP.
//
// The key routing logic being tested:
//   getStandings() checks league.getSport().getSlug() and delegates to the matching API service.
//   This prevents a basketball league from accidentally calling football-data.org (and crashing).
//
// getStandings() uses Spring's Open Session In View (OSIV) to safely lazy-load league.getSport().
// In unit tests there's no Hibernate session at all — the League and Sport objects are
// plain Java objects built with the builder, so .getSport().getSlug() is just a normal getter call.
@ExtendWith(MockitoExtension.class)
class LeagueServiceTest {

    @Mock private LeagueRepository   leagueRepository;
    @Mock private ExternalApiService externalApiService;  // Football (soccer) API
    @Mock private NbaApiService      nbaApiService;       // NBA API (ESPN)
    @Mock private NflApiService      nflApiService;       // NFL API (ESPN)

    @InjectMocks
    private LeagueService leagueService;

    // ── Helpers ───────────────────────────────────────────────────────────────

    // Builds a League entity with a Sport whose slug matches the given value.
    // Mirrors the DB relationships that OSIV lazy-loads in production.
    private static League leagueWithSport(Long id, String sportSlug, String leagueName,
                                          Integer externalId) {
        Sport sport = Sport.builder()
                .id(sportSlug.equals("basketball") ? 2L : sportSlug.equals("american-football") ? 3L : 1L)
                .slug(sportSlug)
                .name(sportSlug)
                .build();
        return League.builder()
                .id(id)
                .sport(sport)
                .name(leagueName)
                .country("Test Country")
                .season("2024-25")
                .externalId(externalId)
                .build();
    }

    // A minimal StandingsEntryDto returned by mock API services
    private static final StandingsEntryDto DUMMY_STANDING = new StandingsEntryDto(
            1,
            new TeamDto(1L, "Test FC", "TFC", null, null, null, 7L),
            10, 7, 1, 2, 18, 9, 22);

    // ── getStandings — 404 on unknown league ──────────────────────────────────

    @Test
    void getStandings_unknownLeagueId_throws404() {
        // If the league doesn't exist in our DB, we can't determine which API to call.
        // Return 404 rather than NullPointerException.
        when(leagueRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leagueService.getStandings(999L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("League not found");

        // No external API should be called for a non-existent league
        verify(nbaApiService, never()).fetchStandings(anyLong());
        verify(nflApiService, never()).fetchStandings(anyLong());
        verify(externalApiService, never()).fetchStandings(anyInt());
    }

    // ── getStandings — routing ─────────────────────────────────────────────────

    @Test
    void getStandings_basketballLeague_routesToNbaApiService() {
        // GIVEN: NBA league (sport slug = "basketball")
        League nbaLeague = leagueWithSport(7L, "basketball", "NBA", null);
        when(leagueRepository.findById(7L)).thenReturn(Optional.of(nbaLeague));
        when(nbaApiService.fetchStandings(7L)).thenReturn(List.of(DUMMY_STANDING));

        // WHEN
        List<StandingsEntryDto> result = leagueService.getStandings(7L);

        // THEN: NBA standings come from NbaApiService
        assertThat(result).containsExactly(DUMMY_STANDING);
        verify(nbaApiService).fetchStandings(7L);
        // Other API services must not be touched
        verify(externalApiService, never()).fetchStandings(anyInt());
        verify(nflApiService, never()).fetchStandings(anyLong());
    }

    @Test
    void getStandings_americanFootballLeague_routesToNflApiService() {
        // GIVEN: NFL league (sport slug = "american-football")
        League nflLeague = leagueWithSport(8L, "american-football", "NFL", null);
        when(leagueRepository.findById(8L)).thenReturn(Optional.of(nflLeague));
        when(nflApiService.fetchStandings(8L)).thenReturn(List.of(DUMMY_STANDING));

        // WHEN
        List<StandingsEntryDto> result = leagueService.getStandings(8L);

        // THEN
        assertThat(result).containsExactly(DUMMY_STANDING);
        verify(nflApiService).fetchStandings(8L);
        verify(nbaApiService, never()).fetchStandings(anyLong());
        verify(externalApiService, never()).fetchStandings(anyInt());
    }

    @Test
    void getStandings_footballLeagueWithExternalId_routesToExternalApiService() {
        // GIVEN: Premier League (sport slug = "football", externalId = 2021)
        League premierLeague = leagueWithSport(1L, "football", "Premier League", 2021);
        when(leagueRepository.findById(1L)).thenReturn(Optional.of(premierLeague));
        when(externalApiService.fetchStandings(2021)).thenReturn(List.of(DUMMY_STANDING));

        // WHEN
        List<StandingsEntryDto> result = leagueService.getStandings(1L);

        // THEN: ExternalApiService is called with the competition's ID
        assertThat(result).containsExactly(DUMMY_STANDING);
        verify(externalApiService).fetchStandings(2021);
        verify(nbaApiService, never()).fetchStandings(anyLong());
        verify(nflApiService, never()).fetchStandings(anyLong());
    }

    @Test
    void getStandings_footballLeagueWithoutExternalId_returnsEmptyList() {
        // Edge case: a football league missing its football-data.org competition ID.
        // We can't fetch standings without the ID — return empty rather than crash.
        League footballLeagueNoId = leagueWithSport(2L, "football", "Unknown League", null);
        when(leagueRepository.findById(2L)).thenReturn(Optional.of(footballLeagueNoId));

        List<StandingsEntryDto> result = leagueService.getStandings(2L);

        assertThat(result).isEmpty();
        verify(externalApiService, never()).fetchStandings(any(Integer.class));
    }

    // ── getLeagueById ─────────────────────────────────────────────────────────

    @Test
    void getLeagueById_existingLeague_returnsDto() {
        // GIVEN: a known football league with all fields populated
        League league = leagueWithSport(1L, "football", "Premier League", 2021);
        when(leagueRepository.findById(1L)).thenReturn(Optional.of(league));

        LeagueDto dto = leagueService.getLeagueById(1L);

        // The DTO should map all fields from the entity
        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.name()).isEqualTo("Premier League");
        assertThat(dto.externalId()).isEqualTo(2021);
        // sportId comes from the nested Sport entity
        assertThat(dto.sportId()).isEqualTo(1L); // football Sport has id=1L in our helper
    }

    @Test
    void getLeagueById_unknownLeague_throws404() {
        // A missing league should produce a descriptive 404 — not a generic 500
        when(leagueRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leagueService.getLeagueById(999L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("League not found");
    }

    // ── getLeaguesBySport ─────────────────────────────────────────────────────

    @Test
    void getLeaguesBySport_returnsAllLeaguesForSport() {
        // GIVEN: a Sport with two leagues
        League pl  = leagueWithSport(1L, "football", "Premier League", 2021);
        League bun = leagueWithSport(2L, "football", "Bundesliga", 2002);
        Sport sport = pl.getSport(); // Both use the same Sport since same slug
        when(leagueRepository.findBySportId(1L)).thenReturn(List.of(pl, bun));

        List<LeagueDto> result = leagueService.getLeaguesBySport(1L);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(LeagueDto::name)
                .containsExactlyInAnyOrder("Premier League", "Bundesliga");
    }

    @Test
    void getLeaguesBySport_noLeagues_returnsEmptyList() {
        // A sport ID that exists but has no leagues should return empty — not throw
        when(leagueRepository.findBySportId(99L)).thenReturn(List.of());

        assertThat(leagueService.getLeaguesBySport(99L)).isEmpty();
    }
}
