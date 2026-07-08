package com.onestopsports.service;

import com.onestopsports.dto.PlayerBioDto;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

// Unit tests for BallDontLieService — no Spring context, no real HTTP calls.
//
// How the mocking works here:
//   BallDontLieService uses RestClient's fluent chain with a VARARGS uri template:
//   restClient.get().uri("/players?search={first}&per_page=10", firstName).retrieve().body(...)
//   That's the same string-template + varargs form NbaApiService uses for fetchPlayersByTeam,
//   so we stub .uri(any(String.class), any(Object[].class)) — same idiom as NbaApiServiceTest.
//
//   We use Mockito's RETURNS_DEEP_STUBS mode which auto-creates sub-mocks for every step in
//   the chain (get() -> uri() -> retrieve() -> body()), so we only need to stub the final
//   body() call with our test fixture.
//
// BallDontLieService has a package-private test constructor (same package = com.onestopsports.service)
// that accepts a pre-built RestClient, which we inject here instead of using @InjectMocks.
//
// IMPORTANT: BdlPlayersResponse and BdlPlayer are package-private records (widened from private
// in plan 01-01) specifically so this test can construct fixtures directly, without going
// through Jackson deserialization from a JSON string.
@SuppressWarnings({"unchecked", "rawtypes"})  // RestClient generics cause unchecked cast warnings in test stubs
@ExtendWith(MockitoExtension.class)
class BallDontLieServiceTest {

    // RETURNS_DEEP_STUBS auto-creates sub-mocks for every method in the chain.
    // This covers: restClient.get() -> uriSpec -> headersSpec -> responseSpec -> body()
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    RestClient restClient;

    // Built manually via the package-private test constructor (rather than @InjectMocks,
    // which would need @Value to resolve the base-url/api-key strings).
    BallDontLieService ballDontLieService;

    @BeforeEach
    void setUp() {
        ballDontLieService = new BallDontLieService(restClient);
    }

    // Builds a single-player balldontlie response. weight is passed as the raw String form
    // the API actually returns (e.g. "223"), exercising searchPlayerByName()'s parse-to-Integer path.
    private BallDontLieService.BdlPlayersResponse responseWith(
            String firstName, String lastName, String height, String weight,
            String college, Integer draftYear, Integer draftRound, Integer draftNumber) {

        var player = new BallDontLieService.BdlPlayer(
                1L, firstName, lastName, "F", height, weight, "7",
                college, "USA", draftYear, draftRound, draftNumber);

        return new BallDontLieService.BdlPlayersResponse(List.of(player));
    }

    // ── searchPlayerByName ───────────────────────────────────────────────────

    @Test
    void searchPlayerByName_firstNameSearchWithLastNameMatch_mapsToPlayerBioDto() {
        // Happy path: "Jaylen Brown" splits into a first-name search ("Jaylen") whose
        // results include an entry with lastName "Brown" — the exact match wins, and every
        // field (including the parsed weight) should flow through to the DTO.
        when(restClient.get().uri(any(String.class), any(Object[].class)).retrieve()
                .body(BallDontLieService.BdlPlayersResponse.class))
                .thenReturn(responseWith("Jaylen", "Brown", "6-6", "223",
                        "California", 2016, 1, 3));

        Optional<PlayerBioDto> result = ballDontLieService.searchPlayerByName("Jaylen Brown");

        assertThat(result).isPresent();
        PlayerBioDto dto = result.get();
        assertThat(dto.height()).isEqualTo("6-6");
        // The API sends weight as a String ("223") — we assert it was parsed to an Integer.
        assertThat(dto.weightPounds()).isEqualTo(223);
        assertThat(dto.college()).isEqualTo("California");
        assertThat(dto.draftYear()).isEqualTo(2016);
        assertThat(dto.draftRound()).isEqualTo(1);
        assertThat(dto.draftNumber()).isEqualTo(3);
    }

    @Test
    void searchPlayerByName_lastNameDoesNotMatch_returnsEmpty() {
        // The first-name search matches, but the candidate's lastName differs from ours
        // (e.g. we searched "Jaylen Brown" but the only "Jaylen" result is "Jaylen Adams") —
        // that's not our player, so we should get Optional.empty(), not a wrong match.
        when(restClient.get().uri(any(String.class), any(Object[].class)).retrieve()
                .body(BallDontLieService.BdlPlayersResponse.class))
                .thenReturn(responseWith("Jaylen", "Adams", "6-0", "205",
                        "St. Bonaventure", 2018, 2, 40));

        Optional<PlayerBioDto> result = ballDontLieService.searchPlayerByName("Jaylen Brown");

        assertThat(result).isEmpty();
    }

    @Test
    void searchPlayerByName_blankName_returnsEmptyWithoutCallingRestClient() {
        // Guard clause: a null/blank name must short-circuit before any HTTP call is made.
        Optional<PlayerBioDto> result = ballDontLieService.searchPlayerByName("   ");

        assertThat(result).isEmpty();
        verifyNoInteractions(restClient);
    }

    @Test
    void searchPlayerByName_nullName_returnsEmptyWithoutCallingRestClient() {
        // Same guard clause, but for a null argument rather than a blank string.
        Optional<PlayerBioDto> result = ballDontLieService.searchPlayerByName(null);

        assertThat(result).isEmpty();
        verifyNoInteractions(restClient);
    }

    @Test
    void searchPlayerByName_restClientThrows_returnsEmptyInsteadOfPropagating() {
        // Soft-fail contract: balldontlie enrichment data is optional — if the call fails
        // (rate-limited, network blip, etc.) we must swallow it and hide the bio section,
        // never propagate an exception up to the controller.
        when(restClient.get().uri(any(String.class), any(Object[].class)).retrieve()
                .body(BallDontLieService.BdlPlayersResponse.class))
                .thenThrow(new RestClientException("balldontlie rate limited"));

        Optional<PlayerBioDto> result = ballDontLieService.searchPlayerByName("Jaylen Brown");

        assertThat(result).isEmpty();
    }
}
