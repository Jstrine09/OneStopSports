package com.onestopsports.service;

import com.onestopsports.dto.MatchDto;
import com.onestopsports.dto.MatchEventDto;
import com.onestopsports.dto.TeamDto;
import com.onestopsports.model.League;
import com.onestopsports.model.Sport;
import com.onestopsports.repository.LeagueRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pure unit tests for MatchService — no Spring context, no database.
// All external dependencies (API services, repositories, messaging) are replaced with Mockito mocks.
//
// The key things being tested:
//   1. Sport-based routing: basketball → NbaApiService, american-football → NflApiService, default → ExternalApiService
//   2. Null/missing input safety (leagueId=null, date=null, unknown leagueId)
//   3. Simple delegation methods (getMatchById, getMatchEvents)
//   4. Stub-only methods (getMatchStats, getMatchLineups return empty maps)
//
// @Transactional has no effect in unit tests — we're not in a Spring context,
// so league.getSport() on real entity objects is just a regular Java getter call.
@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    // All five dependencies are mocked — MatchService never touches a real API or database here
    @Mock private ExternalApiService    externalApiService;
    @Mock private NbaApiService         nbaApiService;
    @Mock private NflApiService         nflApiService;
    @Mock private LeagueRepository      leagueRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;  // Required by the constructor
    @Mock private CacheManager          cacheManager;       // Required by the constructor

    // @InjectMocks creates a real MatchService and injects all six mocks above into its constructor
    @InjectMocks
    private MatchService matchService;

    // A fixed test date — used in all getMatchesByLeagueAndDate calls
    private static final LocalDate TEST_DATE = LocalDate.of(2025, 4, 20);

    // A minimal MatchDto used as a stand-in for whatever the API returns
    private static final MatchDto DUMMY_MATCH = new MatchDto(
            999L,
            new TeamDto(1L, "Home FC", "HFC", null, null, null, 7L),
            new TeamDto(2L, "Away FC", "AFC", null, null, null, 7L),
            2, 1, "FINISHED", LocalDateTime.of(2025, 4, 20, 19, 0), 7L);

    // ── Helpers ───────────────────────────────────────────────────────────────

    // Builds a League with a Sport that has the given slug.
    // Mimics the entity relationships MatchService reads in production.
    private static League leagueWithSport(Long id, String sportSlug, Integer externalId) {
        Sport sport = Sport.builder().slug(sportSlug).build();
        return League.builder().id(id).sport(sport).externalId(externalId).build();
    }

    // ── getMatchesByLeagueAndDate — guard clauses ─────────────────────────────

    @Test
    void getMatchesByLeagueAndDate_nullLeagueId_returnsEmptyList() {
        // A null leagueId is an invalid call — should bail out immediately without touching the DB
        List<MatchDto> result = matchService.getMatchesByLeagueAndDate(null, TEST_DATE);

        assertThat(result).isEmpty();
        // The repository must NOT be queried for invalid input
        verify(leagueRepository, never()).findById(anyLong());
    }

    @Test
    void getMatchesByLeagueAndDate_nullDate_returnsEmptyList() {
        // A null date is invalid — should bail out without touching the DB or external APIs
        List<MatchDto> result = matchService.getMatchesByLeagueAndDate(7L, null);

        assertThat(result).isEmpty();
        verify(leagueRepository, never()).findById(anyLong());
    }

    @Test
    void getMatchesByLeagueAndDate_unknownLeagueId_returnsEmptyList() {
        // leagueId 999 doesn't exist — repository returns empty Optional
        when(leagueRepository.findById(999L)).thenReturn(Optional.empty());

        List<MatchDto> result = matchService.getMatchesByLeagueAndDate(999L, TEST_DATE);

        assertThat(result).isEmpty();
        // No external API should be called if we can't find the league
        verify(nbaApiService, never()).fetchGameDtosByDate(any(), anyLong());
        verify(nflApiService, never()).fetchGameDtosByDate(any(), anyLong());
        // fetchMatchDtosByCompetition takes int (primitive) — use anyInt() to avoid NPE from auto-unboxing null
        verify(externalApiService, never()).fetchMatchDtosByCompetition(anyInt(), any());
    }

    // ── getMatchesByLeagueAndDate — routing ───────────────────────────────────

    @Test
    void getMatchesByLeagueAndDate_basketballLeague_routesToNbaApiService() {
        // GIVEN: a Basketball league in the DB (sport slug = "basketball")
        League nbaLeague = leagueWithSport(7L, "basketball", null);
        when(leagueRepository.findById(7L)).thenReturn(Optional.of(nbaLeague));
        when(nbaApiService.fetchGameDtosByDate(TEST_DATE, 7L)).thenReturn(List.of(DUMMY_MATCH));

        // WHEN
        List<MatchDto> result = matchService.getMatchesByLeagueAndDate(7L, TEST_DATE);

        // THEN: NbaApiService was called and the result flows through unchanged
        assertThat(result).containsExactly(DUMMY_MATCH);
        verify(nbaApiService).fetchGameDtosByDate(TEST_DATE, 7L);
        // Football and NFL services must NOT be called for a basketball league
        verify(externalApiService, never()).fetchMatchDtosByCompetition(anyInt(), any());
        verify(nflApiService, never()).fetchGameDtosByDate(any(), anyLong());
    }

    @Test
    void getMatchesByLeagueAndDate_americanFootballLeague_routesToNflApiService() {
        // GIVEN: an NFL league in the DB (sport slug = "american-football")
        League nflLeague = leagueWithSport(8L, "american-football", null);
        when(leagueRepository.findById(8L)).thenReturn(Optional.of(nflLeague));
        when(nflApiService.fetchGameDtosByDate(TEST_DATE, 8L)).thenReturn(List.of(DUMMY_MATCH));

        // WHEN
        List<MatchDto> result = matchService.getMatchesByLeagueAndDate(8L, TEST_DATE);

        // THEN
        assertThat(result).containsExactly(DUMMY_MATCH);
        verify(nflApiService).fetchGameDtosByDate(TEST_DATE, 8L);
        verify(nbaApiService, never()).fetchGameDtosByDate(any(), anyLong());
        verify(externalApiService, never()).fetchMatchDtosByCompetition(anyInt(), any());
    }

    @Test
    void getMatchesByLeagueAndDate_footballLeagueWithExternalId_routesToExternalApiService() {
        // GIVEN: a football league with a football-data.org competition ID
        League premierLeague = leagueWithSport(1L, "football", 2021);
        when(leagueRepository.findById(1L)).thenReturn(Optional.of(premierLeague));
        when(externalApiService.fetchMatchDtosByCompetition(2021, TEST_DATE))
                .thenReturn(List.of(DUMMY_MATCH));

        // WHEN
        List<MatchDto> result = matchService.getMatchesByLeagueAndDate(1L, TEST_DATE);

        // THEN: ExternalApiService is called with the competition's externalId
        assertThat(result).containsExactly(DUMMY_MATCH);
        verify(externalApiService).fetchMatchDtosByCompetition(eq(2021), eq(TEST_DATE));
        verify(nbaApiService, never()).fetchGameDtosByDate(any(), anyLong());
        verify(nflApiService, never()).fetchGameDtosByDate(any(), anyLong());
    }

    @Test
    void getMatchesByLeagueAndDate_footballLeagueWithoutExternalId_returnsEmptyList() {
        // GIVEN: a football league that has no externalId (shouldn't happen in practice,
        // but we guard against it to avoid a NullPointerException)
        League leagueWithNoExternalId = leagueWithSport(2L, "football", null); // externalId = null
        when(leagueRepository.findById(2L)).thenReturn(Optional.of(leagueWithNoExternalId));

        // WHEN
        List<MatchDto> result = matchService.getMatchesByLeagueAndDate(2L, TEST_DATE);

        // THEN: nothing is fetched — we can't call football-data.org without the competition ID
        assertThat(result).isEmpty();
        verify(externalApiService, never()).fetchMatchDtosByCompetition(anyInt(), any());
    }

    // ── getMatchById ──────────────────────────────────────────────────────────

    @Test
    void getMatchById_nullId_returnsNull() {
        // Guard clause — null ID should return null immediately without calling the API
        assertThat(matchService.getMatchById(null)).isNull();
        verify(externalApiService, never()).fetchMatchById(anyLong());
    }

    @Test
    void getMatchById_validId_delegatesToExternalApiService() {
        // getMatchById is football-only — it always goes to ExternalApiService (no sport routing)
        when(externalApiService.fetchMatchById(12345L)).thenReturn(DUMMY_MATCH);

        MatchDto result = matchService.getMatchById(12345L);

        assertThat(result).isEqualTo(DUMMY_MATCH);
        verify(externalApiService).fetchMatchById(12345L);
    }

    // ── getMatchEvents ────────────────────────────────────────────────────────

    @Test
    void getMatchEvents_nullMatchId_returnsEmptyList() {
        // Guard clause — null matchId means no events to fetch
        assertThat(matchService.getMatchEvents(null)).isEmpty();
        verify(externalApiService, never()).fetchMatchEventDtos(anyLong());
    }

    @Test
    void getMatchEvents_validMatchId_delegatesToExternalApiService() {
        // Match events (goals, cards, subs) are always fetched from football-data.org
        MatchEventDto goal = new MatchEventDto("GOAL", 22, null, "Haaland", null, "Man City");
        when(externalApiService.fetchMatchEventDtos(12345L)).thenReturn(List.of(goal));

        List<MatchEventDto> result = matchService.getMatchEvents(12345L);

        assertThat(result).containsExactly(goal);
        verify(externalApiService).fetchMatchEventDtos(12345L);
    }

    // ── getMatchStats / getMatchLineups ───────────────────────────────────────

    @Test
    void getMatchStats_alwaysReturnsEmptyMap() {
        // Stats are not available on the free tier of football-data.org.
        // This method is a stub — it always returns an empty map regardless of input.
        Map<String, Object> stats = matchService.getMatchStats(12345L);

        assertThat(stats).isEmpty();
    }

    @Test
    void getMatchLineups_alwaysReturnsEmptyMap() {
        // Lineups are not available on the free tier of football-data.org.
        // Same stub pattern as getMatchStats.
        Map<String, Object> lineups = matchService.getMatchLineups(12345L);

        assertThat(lineups).isEmpty();
    }
}
