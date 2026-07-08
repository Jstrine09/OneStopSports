package com.onestopsports.service;

import com.onestopsports.dto.PlayerCareerStatsDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Unit tests for ApiFootballService — no Spring context, no real HTTP calls.
//
// How the mocking works here:
//   ApiFootballService uses RestClient's fluent chain, but unlike NbaApiService it builds
//   the request URI via a Function<UriBuilder, URI> lambda (uri -> uri.path(...).queryParam(...).build())
//   rather than a plain string template. So we stub .uri(any(Function.class)) instead of
//   .uri(anyString()) — the exact URL built by the lambda doesn't matter to the mock, only
//   that "some Function was passed" needs to match.
//
//   We use Mockito's RETURNS_DEEP_STUBS mode which auto-creates sub-mocks for every step in
//   the chain (get() -> uri() -> retrieve() -> body()), so we only need to stub the final
//   body() call with our test fixture.
//
// ApiFootballService has a package-private test constructor (same package = com.onestopsports.service)
// that accepts a pre-built RestClient, which we inject here instead of using @InjectMocks.
@SuppressWarnings({"unchecked", "rawtypes"})  // RestClient generics cause unchecked cast warnings in test stubs
@ExtendWith(MockitoExtension.class)
class ApiFootballServiceTest {

    // RETURNS_DEEP_STUBS auto-creates sub-mocks for every method in the chain.
    // This covers: restClient.get() -> uriSpec -> headersSpec -> responseSpec -> body()
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    RestClient restClient;

    // Built manually via the package-private test constructor (rather than @InjectMocks,
    // which would need @Value to resolve the base-url/api-key strings).
    ApiFootballService apiFootballService;

    @BeforeEach
    void setUp() {
        apiFootballService = new ApiFootballService(restClient);
    }

    // The same curated column set ApiFootballService uses internally for football stats.
    // It's private in production code, so we duplicate the literal here purely to assert
    // against it (this is NOT testing an implementation detail we invented — it's the
    // public contract of fetchPlayerStats()'s returned DTO).
    private static final List<String> FOOTBALL_LABELS = List.of(
            "APPS", "MIN", "GOALS", "AST", "SHOTS", "ON", "PASS%", "YEL", "RED", "RATING"
    );

    // ── Helpers: build API-SPORTS response fixtures ────────────────────────────

    // Builds a search response containing a single player entry with no statistics attached
    // (statistics aren't needed for the searchPlayerId tests — only the player id/name).
    private ApiFootballService.ApiResponse searchResponseWith(int id, String firstname, String lastname) {
        var player = new ApiFootballService.ApiPlayer(id, firstname, lastname);
        var entry  = new ApiFootballService.ApiPlayerEntry(player, null);
        return new ApiFootballService.ApiResponse(List.of(entry));
    }

    // Builds a stats-lookup response with one player entry carrying a single stat block —
    // this is the shape fetchPlayerStats() maps into a PlayerCareerStatsDto.
    private ApiFootballService.ApiResponse statsResponseWith(String teamName, String leagueName) {
        var team   = new ApiFootballService.ApiTeam(85, teamName);
        var league = new ApiFootballService.ApiLeague(leagueName);
        var games  = new ApiFootballService.ApiGames(28, 2400, "7.85", "Attacker");
        var shots  = new ApiFootballService.ApiShots(90, 45);
        var goals  = new ApiFootballService.ApiGoals(22, 8);
        var passes = new ApiFootballService.ApiPasses(700, 30, 82);
        var cards  = new ApiFootballService.ApiCards(3, 0, 0);
        var block  = new ApiFootballService.ApiStatBlock(team, league, games, shots, goals, passes, cards);

        var player = new ApiFootballService.ApiPlayer(230, "Erling", "Haaland");
        var entry  = new ApiFootballService.ApiPlayerEntry(player, List.of(block));
        return new ApiFootballService.ApiResponse(List.of(entry));
    }

    // ── searchPlayerId ───────────────────────────────────────────────────────

    @Test
    void searchPlayerId_exactNameMatch_returnsPlayerId() {
        // Happy path: the response contains an entry whose "firstname lastname" exactly
        // matches our query (after accent-stripping/lowercasing), so we should get that id.
        when(restClient.get().uri(any(Function.class)).retrieve()
                .body(ApiFootballService.ApiResponse.class))
                .thenReturn(searchResponseWith(230, "Erling", "Haaland"));

        Optional<Integer> result = apiFootballService.searchPlayerId("Erling Haaland", 39, 2024);

        assertThat(result).contains(230);
    }

    @Test
    void searchPlayerId_accentedQueryMatchesPlainAsciiApiName_returnsPlayerId() {
        // The DB may store an accented name ("Dembélé") while the API returns the plain
        // ASCII form ("Dembele") or vice versa. Both sides are accent-stripped before
        // comparison, so an accented query should still match a plain-ASCII API entry.
        when(restClient.get().uri(any(Function.class)).retrieve()
                .body(ApiFootballService.ApiResponse.class))
                .thenReturn(searchResponseWith(410, "Ousmane", "Dembele"));

        Optional<Integer> result = apiFootballService.searchPlayerId("Ousmane Dembélé", 39, 2024);

        assertThat(result).contains(410);
    }

    @Test
    void searchPlayerId_nameTooShortToSearch_returnsEmptyWithoutCallingRestClient() {
        // Both the first and last "word" are under 4 characters after accent-stripping, so
        // the service should bail out BEFORE making an HTTP call at all (the free tier's
        // search param requires >= 4 chars). We assert zero interaction with the mock RestClient.
        Optional<Integer> result = apiFootballService.searchPlayerId("Al Bo", 39, 2024);

        assertThat(result).isEmpty();
        verifyNoInteractions(restClient);
    }

    @Test
    void searchPlayerId_restClientThrows_returnsEmptyInsteadOfPropagating() {
        // Soft-fail contract: a 429 (rate-limited), 500, or network blip must be swallowed —
        // the stats card just stays hidden on the frontend, no 500 propagates to the client.
        when(restClient.get().uri(any(Function.class)).retrieve()
                .body(ApiFootballService.ApiResponse.class))
                .thenThrow(new RestClientException("API-SPORTS rate limited"));

        Optional<Integer> result = apiFootballService.searchPlayerId("Erling Haaland", 39, 2024);

        assertThat(result).isEmpty();
    }

    // ── fetchPlayerStats ─────────────────────────────────────────────────────

    @Test
    void fetchPlayerStats_happyPath_mapsToFootballCareerStatsDto() {
        // Happy path: one stat block for one team/competition maps to a single "season"
        // StatCategory using the curated FOOTBALL_LABELS column set.
        when(restClient.get().uri(any(Function.class)).retrieve()
                .body(ApiFootballService.ApiResponse.class))
                .thenReturn(statsResponseWith("Manchester City", "Premier League"));

        PlayerCareerStatsDto result = apiFootballService.fetchPlayerStats(230, 2024);

        assertThat(result).isNotNull();
        assertThat(result.sport()).isEqualTo("football");
        assertThat(result.categories()).hasSize(1);

        PlayerCareerStatsDto.StatCategory category = result.categories().get(0);
        assertThat(category.labels()).isEqualTo(FOOTBALL_LABELS);
        // Free tier is single-season only — there's no career aggregate row.
        assertThat(category.career()).isNull();

        assertThat(category.seasons()).hasSize(1);
        PlayerCareerStatsDto.SeasonRow row = category.seasons().get(0);
        assertThat(row.team()).isEqualTo("Manchester City");
        assertThat(row.competition()).isEqualTo("Premier League");
        // values[] is aligned with FOOTBALL_LABELS: APPS, MIN, GOALS, AST, SHOTS, ON, PASS%, YEL, RED, RATING
        assertThat(row.values()).containsExactly("28", "2400", "22", "8", "90", "45", "82%", "3", "0", "7.85");
    }

    @Test
    void fetchPlayerStats_emptyResponse_returnsNull() {
        // No statistics found for this player/season — fetchPlayerStats returns null
        // (the caller / GlobalExceptionHandler turns this into a 204 for the client).
        when(restClient.get().uri(any(Function.class)).retrieve()
                .body(ApiFootballService.ApiResponse.class))
                .thenReturn(new ApiFootballService.ApiResponse(List.of()));

        PlayerCareerStatsDto result = apiFootballService.fetchPlayerStats(230, 2024);

        assertThat(result).isNull();
    }

    @Test
    void fetchPlayerStats_restClientThrows_returnsNullInsteadOfPropagating() {
        // Same soft-fail contract as searchPlayerId — a failed upstream call must not
        // surface as a 500; fetchPlayerStats returns null and the frontend hides the card.
        when(restClient.get().uri(any(Function.class)).retrieve()
                .body(ApiFootballService.ApiResponse.class))
                .thenThrow(new RestClientException("API-SPORTS unavailable"));

        PlayerCareerStatsDto result = apiFootballService.fetchPlayerStats(230, 2024);

        assertThat(result).isNull();
    }
}
