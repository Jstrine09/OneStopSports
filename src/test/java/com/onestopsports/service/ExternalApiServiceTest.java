package com.onestopsports.service;

import com.onestopsports.dto.BoxScoreDto;
import com.onestopsports.dto.StandingsEntryDto;
import com.onestopsports.repository.LeagueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// Unit tests for ExternalApiService — no Spring context, no real HTTP calls.
//
// How the mocking works here:
//   ExternalApiService uses RestClient's fluent chain: restClient.get().uri(...).retrieve().body(Class)
//   Each step returns a different interface type. We use Mockito's RETURNS_DEEP_STUBS mode so a
//   single mock auto-creates sub-mocks for every step in the chain, then we stub just the final
//   body() call with our test fixture. The endpoints under test here (standings, match detail)
//   use the .uri(template, args) varargs overload, so we match with any(String.class), any(Object[].class).
//
// ExternalApiService also depends on LeagueRepository (to translate football-data.org competition
// IDs into our DB league IDs) — that dependency isn't exercised by fetchStandings/fetchFootballBoxScore
// so a plain @Mock with no stubbing is enough.
@SuppressWarnings({"unchecked", "rawtypes"})  // RestClient generics cause unchecked cast warnings in test stubs
@ExtendWith(MockitoExtension.class)
class ExternalApiServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    RestClient restClient;

    @Mock
    LeagueRepository leagueRepository;

    // We construct ExternalApiService manually using the package-private test constructor
    // (rather than @InjectMocks, which needs @Value to resolve the base URL / API key strings)
    ExternalApiService externalApiService;

    @BeforeEach
    void setUp() {
        externalApiService = new ExternalApiService(restClient, leagueRepository);
    }

    // ── Helpers: build football-data.org response objects ─────────────────────

    // Builds a minimal ApiMatchTeam — the compact team shape used inside standings/match rows.
    private ExternalApiService.ApiMatchTeam matchTeam(Long id, String name) {
        return new ExternalApiService.ApiMatchTeam(id, name, name, "crest.png");
    }

    // Builds a standings response with a single group of the given type, containing one entry.
    private ExternalApiService.ApiStandingsResponse standingsWithGroup(String groupType) {
        var entry = new ExternalApiService.ApiStandingEntry(
                1, matchTeam(65L, "Manchester City FC"),
                30, 21, 5, 4, 72, 30, 68);
        var group = new ExternalApiService.ApiStandingGroup(groupType, List.of(entry));
        return new ExternalApiService.ApiStandingsResponse(null, List.of(group));
    }

    // ── fetchStandings ────────────────────────────────────────────────────────

    @Test
    void fetchStandings_totalGroup_mapsEntryWithNullConferenceDivisionPctGamesBehind() {
        // HAPPY PATH: the TOTAL standings group's single entry should map to a StandingsEntryDto
        // with position/won/drawn(from draw)/lost/goalsFor/goalsAgainst/points carried straight
        // across, and conference/division/pct/gamesBehind all null — football has none of those.
        when(restClient.get().uri(any(String.class), any(Object[].class)).retrieve()
                .body(ExternalApiService.ApiStandingsResponse.class))
                .thenReturn(standingsWithGroup("TOTAL"));

        List<StandingsEntryDto> result = externalApiService.fetchStandings(2021);

        assertThat(result).hasSize(1);
        StandingsEntryDto entry = result.get(0);
        assertThat(entry.position()).isEqualTo(1);
        assertThat(entry.won()).isEqualTo(21);
        assertThat(entry.drawn()).isEqualTo(5); // API field is "draw", DTO field is "drawn"
        assertThat(entry.lost()).isEqualTo(4);
        assertThat(entry.goalsFor()).isEqualTo(72);
        assertThat(entry.goalsAgainst()).isEqualTo(30);
        assertThat(entry.points()).isEqualTo(68);
        assertThat(entry.conference()).isNull();
        assertThat(entry.division()).isNull();
        assertThat(entry.pct()).isNull();
        assertThat(entry.gamesBehind()).isNull();
    }

    @Test
    void fetchStandings_noTotalGroup_returnsEmptyList() {
        // FILTER: football-data.org also returns HOME and AWAY splits — a response whose
        // only group is NOT "TOTAL" must yield an empty list, not the wrong group's data.
        when(restClient.get().uri(any(String.class), any(Object[].class)).retrieve()
                .body(ExternalApiService.ApiStandingsResponse.class))
                .thenReturn(standingsWithGroup("HOME"));

        List<StandingsEntryDto> result = externalApiService.fetchStandings(2021);

        assertThat(result).isEmpty();
    }

    @Test
    void fetchStandings_restClientException_returnsEmptyListWithoutThrowing() {
        // SOFT-FAIL: a failed standings call (e.g. 403 invalid key, 429 rate limit) must be
        // caught and return an empty list — NOT propagate a 500 to the client.
        when(restClient.get().uri(any(String.class), any(Object[].class)).retrieve()
                .body(ExternalApiService.ApiStandingsResponse.class))
                .thenThrow(new RestClientException("football-data.org unavailable"));

        List<StandingsEntryDto> result = externalApiService.fetchStandings(2021);

        assertThat(result).isEmpty();
    }

    // ── fetchFootballBoxScore ─────────────────────────────────────────────────

    @Test
    void fetchFootballBoxScore_restClientException_returnsNull() {
        // SOFT-FAIL: if the underlying GET /matches/{id} call throws, fetchFootballBoxScore
        // must catch it and return null rather than propagating.
        when(restClient.get().uri(any(String.class), any(Object[].class)).retrieve()
                .body(ExternalApiService.ApiMatchDetail.class))
                .thenThrow(new RestClientException("football-data.org unavailable"));

        BoxScoreDto result = externalApiService.fetchFootballBoxScore(12345L);

        assertThat(result).isNull();
    }

    @Test
    void fetchFootballBoxScore_nullDetailBody_returnsNull() {
        // SOFT-FAIL: an unknown match ID returns a null body from football-data.org —
        // fetchFootballBoxScore must return null instead of throwing an NPE.
        when(restClient.get().uri(any(String.class), any(Object[].class)).retrieve()
                .body(ExternalApiService.ApiMatchDetail.class))
                .thenReturn(null);

        BoxScoreDto result = externalApiService.fetchFootballBoxScore(12345L);

        assertThat(result).isNull();
    }
}
