# Phase 1: Backend Service Test Coverage - Pattern Map

**Mapped:** 2026-07-08
**Files analyzed:** 12 new test files (or 11 if PlayerService gaps are folded into one file) + 4 modified production files
**Analogs found:** 12 / 12 (100% — this repo already has a complete precedent set; RESEARCH.md's Code Examples section already contains verbatim excerpts, reconfirmed here against the actual source)

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `src/test/java/com/onestopsports/service/NflApiServiceTest.java` | test (external-API-adapter) | request-response (RestClient chain) | `src/test/java/com/onestopsports/service/NbaApiServiceTest.java` | exact |
| `src/test/java/com/onestopsports/service/ExternalApiServiceTest.java` | test (external-API-adapter) | request-response | `NbaApiServiceTest.java` | exact (add `LeagueRepository` mock) |
| `src/test/java/com/onestopsports/service/ApiFootballServiceTest.java` | test (external-API-adapter) | request-response | `NbaApiServiceTest.java` | exact |
| `src/test/java/com/onestopsports/service/BallDontLieServiceTest.java` | test (external-API-adapter) | request-response | `NbaApiServiceTest.java` | exact (single-client shape, private→package-private record widening needed first) |
| `src/test/java/com/onestopsports/service/UserServiceTest.java` | test (plain service) | CRUD | `src/test/java/com/onestopsports/service/MatchServiceTest.java` | exact (`@InjectMocks` + `@Mock` collaborators) |
| `src/test/java/com/onestopsports/service/SportServiceTest.java` | test (plain service) | CRUD | `MatchServiceTest.java` | role-match (simplest case — 1 dependency) |
| `src/test/java/com/onestopsports/service/PlayerServiceTest.java` | test (plain service) | CRUD + transform | `src/test/java/com/onestopsports/service/PlayerServiceCareerStatsTest.java` | exact (same class under test, sibling file, reuse fixture-helper style) |
| `src/test/java/com/onestopsports/controller/GlobalExceptionHandlerTest.java` (or `...DispatchTest`) | test (controller-advice / MVC dispatch) | request-response | `src/test/java/com/onestopsports/controller/AuthControllerTest.java` | role-match (MockMvc pattern; uses `standaloneSetup` instead of `@WebMvcTest` — see Pattern 4 below) |
| `src/main/java/com/onestopsports/service/NflApiService.java` (add ctor) | service (production edit) | — | `src/main/java/com/onestopsports/service/NbaApiService.java` lines 60-78 (package-private test ctor) | exact |
| `src/main/java/com/onestopsports/service/ExternalApiService.java` (add ctor) | service (production edit) | — | `NbaApiService.java` package-private ctor | exact (adapted: 1 RestClient + `LeagueRepository`, not 3 RestClients) |
| `src/main/java/com/onestopsports/service/ApiFootballService.java` (add ctor) | service (production edit) | — | `NbaApiService.java` package-private ctor | exact (adapted: 1 RestClient) |
| `src/main/java/com/onestopsports/service/BallDontLieService.java` (add ctor + widen records) | service (production edit) | — | `NbaApiService.java` package-private ctor + comment on why response records must be package-private, not `private` | exact |

## Pattern Assignments

### `src/test/java/com/onestopsports/service/NflApiServiceTest.java`

**Analog:** `src/test/java/com/onestopsports/service/NbaApiServiceTest.java` (full file, 308 lines — read in full, no analog file is >2000 lines so a single read sufficed)

**Imports pattern** (lines 1-20 of the analog):
```java
package com.onestopsports.service;

import com.onestopsports.dto.MatchDto;
import com.onestopsports.dto.StandingsEntryDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Answers;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
```
For `NflApiServiceTest`, swap `MatchDto`/`StandingsEntryDto` imports as needed and keep the exact same `@ExtendWith(MockitoExtension.class)` + `@SuppressWarnings({"unchecked", "rawtypes"})` class-level annotations (analog lines 35-37).

**Deep-stub setup pattern** (analog lines 39-58):
```java
@Mock(answer = Answers.RETURNS_DEEP_STUBS)
RestClient restClient;

@Mock(answer = Answers.RETURNS_DEEP_STUBS)
RestClient standingsClient;

@Mock(answer = Answers.RETURNS_DEEP_STUBS)
RestClient statsClient;

NbaApiService nbaApiService;   // → NflApiService nflApiService;

@BeforeEach
void setUp() {
    nbaApiService = new NbaApiService(restClient, standingsClient, statsClient);
    // → nflApiService = new NflApiService(restClient, standingsClient, statsClient);
}
```
`NflApiService` has the identical 3-`RestClient` shape (confirmed: `NflApiService.java` lines 45-91 — `private final RestClient restClient/standingsClient/statsClient`, one public `@Value`-driven constructor). Use the same 3-mock setup verbatim.

**Core fluent-chain stubbing pattern** (analog lines 107-118, 198-220):
```java
when(restClient.get().uri(anyString()).retrieve()
        .body(NbaApiService.EspnScoreboardResponse.class))    // → NflApiService.Espn...Response.class
        .thenReturn(null);

List<MatchDto> result = nbaApiService.fetchGameDtosByDate(LocalDate.of(2025, 4, 20), 7L);

assertThat(result).isEmpty();
```
and for a `.uri(String, Object...)` varargs call (analog lines 184-194):
```java
when(restClient.get().uri(any(String.class), any(Object[].class)).retrieve()
        .body(NbaApiService.EspnRosterResponse.class))
        .thenReturn(null);
```

**Soft-fail / exception pattern** (analog lines 198-210):
```java
when(standingsClient.get().uri(anyString()).retrieve()
        .body(NbaApiService.EspnStandingsResponse.class))
        .thenThrow(new RestClientException("ESPN standings unavailable"));

List<StandingsEntryDto> result = nbaApiService.fetchStandings(7L);

assertThat(result).isEmpty();
```
Apply verbatim to `NflApiService.fetchStandings` / `fetchGameDtosByDate` (both wrap `RestClientException` → empty list per RESEARCH.md's confirmed soft-fail contract).

**Fixture-builder pattern** (analog lines 61-103): private helper methods that build the service's own nested response records directly (`scoreboardWith(...)`, `standingsWithTwo(...)`) — no JSON strings. Mirror this exactly using `NflApiService`'s own `Espn*` record types (all `public`/package-private on that class per RESEARCH.md, unlike `BallDontLieService`).

---

### `src/test/java/com/onestopsports/service/ExternalApiServiceTest.java`

**Analog:** `NbaApiServiceTest.java` (same deep-stub pattern) — but only ONE `RestClient` mock is needed (no standings/stats split), plus a `@Mock LeagueRepository leagueRepository` since `ExternalApiService`'s production constructor takes `(baseUrl, apiKey, LeagueRepository)` (confirmed `ExternalApiService.java` lines 34-47).

**Constructor call in `@BeforeEach`:**
```java
externalApiService = new ExternalApiService(restClient, leagueRepository); // package-private test ctor
```

**Routing/never() verification idiom** — reuse `MatchServiceTest`'s primitive-arg gotcha directly, since `ExternalApiService.fetchMatchDtosByCompetition(int, LocalDate)` takes a primitive `int` (confirmed in `MatchServiceTest.java` line 110 comment):
```java
// fetchMatchDtosByCompetition takes int (primitive) — use anyInt() to avoid NPE from auto-unboxing null
verify(externalApiService, never()).fetchMatchDtosByCompetition(anyInt(), any());
```

---

### `src/test/java/com/onestopsports/service/ApiFootballServiceTest.java`

**Analog:** `NbaApiServiceTest.java` — single `RestClient` mock (`ApiFootballService`'s production ctor takes only `baseUrl` + `apiKey`, confirmed `ApiFootballService.java` lines 45-65).

**Constructor call:**
```java
apiFootballService = new ApiFootballService(restClient); // package-private test ctor
```

**Primitive-arg gotcha** — `ApiFootballService.fetchPlayerStats(int, int)` / `searchPlayerId(String, int, int)` both take `int` params (confirmed `PlayerService.java` lines 136, 156 call sites). Use `anyInt()` for both, per the exact precedent in `MatchServiceTest.java` line 110 and RESEARCH.md's Anti-Patterns section.

---

### `src/test/java/com/onestopsports/service/BallDontLieServiceTest.java`

**Analog:** `NbaApiServiceTest.java` — single `RestClient` mock (`BallDontLieService`'s ctor takes `baseUrl` + `apiKey`, confirmed `BallDontLieService.java` lines 23-33).

**Prerequisite production edit (must land first or test file won't compile):** widen `BdlPlayersResponse`/`BdlPlayer` from `private record` to package-private `record` — confirmed at `BallDontLieService.java` lines 119, 125 (`private record BdlPlayersResponse(...)`, `private record BdlPlayer(...)`). Drop the `private` modifier only; no other change. Reference comment style: `NbaApiService`'s own package-private response records carry a comment explaining why (per RESEARCH.md Pitfall 4) — replicate that comment style here.

**Constructor call:**
```java
ballDontLieService = new BallDontLieService(restClient); // package-private test ctor
```

---

### `src/test/java/com/onestopsports/service/UserServiceTest.java`

**Analog:** `src/test/java/com/onestopsports/service/MatchServiceTest.java` (full file, 243 lines, read in full) for the `@InjectMocks`/`@Mock` shape; `TeamServiceTest.java` also consistent (not separately excerpted — same convention).

**Mock/InjectMocks pattern** (`MatchServiceTest.java` lines 43-56, adapted to `UserService`'s actual 7 constructor params confirmed at `UserService.java` lines 25-35):
```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock private UserRepository userRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private FavoriteTeamRepository favoriteTeamRepository;
    @Mock private FavoritePlayerRepository favoritePlayerRepository;
    @Mock private TeamService teamService;
    @Mock private PlayerService playerService;

    @InjectMocks
    private UserService userService;
}
```

**Guard-clause / never() idiom** (`MatchServiceTest.java` lines 79-96):
```java
@Test
void someMethod_invalidInput_returnsEmptyOrThrows() {
    // ...
    assertThat(result).isEmpty();
    verify(someRepository, never()).findById(anyLong());
}
```
Apply to `UserService`'s favourite-add/remove guard clauses (404 on missing user/team/player, skip-if-already-favourited — per RESEARCH.md's ASVS V4 note).

**`ResponseStatusException` 404 pattern** — `UserService` throws `ResponseStatusException(HttpStatus.NOT_FOUND, ...)` the same way `SportService.getSportBySlug` does (confirmed `SportService.java` line 37):
```java
.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "..."));
```
Assert with AssertJ's `assertThatThrownBy(...).isInstanceOf(ResponseStatusException.class)` — no existing precedent test does this exact assertion shape in-repo, so this is a reasonable minimal extension of the established AssertJ-everywhere convention.

---

### `src/test/java/com/onestopsports/service/SportServiceTest.java`

**Analog:** `MatchServiceTest.java`'s `@InjectMocks` shape, scaled down — `SportService` has exactly one dependency (confirmed `SportService.java` lines 15-22: `private final SportRepository sportRepository`).
```java
@ExtendWith(MockitoExtension.class)
class SportServiceTest {
    @Mock private SportRepository sportRepository;

    @InjectMocks
    private SportService sportService;
}
```
Two methods to cover: `getAllSports()` (maps `findAll()` → `toDto`) and `getSportBySlug(slug)` (200 vs `ResponseStatusException` 404 via `findBySlug` → `Optional.empty()`, mirroring the `orElseThrow` pattern at `SportService.java` line 37).

---

### `src/test/java/com/onestopsports/service/PlayerServiceTest.java`

**Analog:** `src/test/java/com/onestopsports/service/PlayerServiceCareerStatsTest.java` (existing sibling file for the same class under test — same package, same fixture-building convention). Per RESEARCH.md's Open Question 1/recommendation, this is a NEW separate file (leave `PlayerServiceCareerStatsTest.java` untouched), covering `resolvePhotoUrl`, `toDto`, `searchPlayers` only (confirmed these three are the untested methods in `PlayerService.java`, lines 184-237 for `toDto`/`resolvePhotoUrl`, lines 176-182 for `searchPlayers`).

**Routing-verification idiom** (reuse verbatim from `PlayerServiceCareerStatsTest`, per RESEARCH.md's Code Examples section):
```java
@Test
void getPlayerCareerStats_basketballWithExternalId_routesToNba() {
    Player lebron = playerInSport(1L, "basketball", "1966", "LeBron James", null);
    when(playerRepository.findById(1L)).thenReturn(Optional.of(lebron));
    when(nbaApiService.fetchCareerStats("1966")).thenReturn(DUMMY_STATS);

    Optional<PlayerCareerStatsDto> result = playerService.getPlayerCareerStats(1L);

    assertThat(result).contains(DUMMY_STATS);
    verify(nbaApiService).fetchCareerStats("1966");
    verify(nflApiService, never()).fetchCareerStats(anyString());
    verify(apiFootballService, never()).fetchPlayerStats(anyInt(), anyInt());
}
```
Apply the same `never()`-on-wrong-service idiom to `resolvePhotoUrl`'s three-branch switch (confirmed `PlayerService.java` lines 214-236: `basketball` → NBA CDN URL, `american-football` → NFL CDN URL, default/football → null, plus the persisted-`photoUrl`-wins-first layer at lines 216-218). Note `resolvePhotoUrl` and `toDto` are `private static`/package-private and called only through public methods (`getPlayerById`, `getPlayersByTeam`, `searchPlayers`) — test via those public entry points, building a `Player → Team → Sport` fixture chain (reuse a minimal version of `PlayerServiceCareerStatsTest.playerInSport(...)` per RESEARCH.md's recommendation — duplicate a small private helper rather than extracting a shared fixture utility, matching the codebase's no-shared-fixtures convention).

**`searchPlayers` test:** stub `playerRepository.findByNameNormalizedContaining(normalized)` (confirmed `PlayerService.java` line 178) and assert the `.limit(10)` cap and `toDto` mapping — same `@InjectMocks`/`@Mock` shape as `PlayerServiceCareerStatsTest`.

---

### `src/test/java/com/onestopsports/controller/GlobalExceptionHandlerTest.java`

**Analog:** `src/test/java/com/onestopsports/controller/AuthControllerTest.java` (full file, 150 lines) for the general MockMvc/`jsonPath` assertion style — BUT per RESEARCH.md Pitfall 1, do NOT reuse `@WebMvcTest` + `@Import(SecurityConfig.class)` here (that pulls in filters/security irrelevant to proving `@ExceptionHandler` dispatch order). Use `MockMvcBuilders.standaloneSetup(...).setControllerAdvice(...)` instead — a lighter, no-Spring-context alternative documented in RESEARCH.md's Code Examples section:
```java
@RestController
class ThrowingTestController {
    @GetMapping("/test/not-found")
    void notFound() { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found: 999"); }

    @GetMapping("/test/boom")
    void boom() { throw new RuntimeException("unexpected"); }
}

class GlobalExceptionHandlerDispatchTest {
    MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ThrowingTestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void responseStatusException_dispatchesToPassthroughHandler_not404ViaCatchAll() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Player not found: 999"));
    }

    @Test
    void unhandledRuntimeException_dispatchesToCatchAll() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }
}
```
`jsonPath(...)` assertion style and `MediaType`/status matcher imports are reused directly from `AuthControllerTest.java` lines 20-24 (static imports block).

**Plain-call unit tests for the other 7 handler methods** (`handleValidation`, `handleUnreadable`, `handleTypeMismatch`, `handleMissingParam`, `handleMethodNotSupported`, `handleBadCredentials`, `handleAccessDenied`, `handleDataIntegrity`) — no MockMvc needed, just `new GlobalExceptionHandler().handleX(new XException(...))` and assert on the returned `ResponseEntity<ErrorResponseDto>` fields (status code + body). Confirmed exact status codes and message-building logic at `GlobalExceptionHandler.java` lines 37-149 (400/400/400/400/405/401/403/409/500 in that order — see the file's own inline comments for what triggers each).

---

## Shared Patterns

### Package-private test constructor (production-code change)
**Source:** `src/main/java/com/onestopsports/service/NbaApiService.java` lines 60-78 (referenced by RESEARCH.md; verified against `NflApiService.java` lines 45-91, `ExternalApiService.java` lines 34-47, `ApiFootballService.java` lines 45-65, `BallDontLieService.java` lines 23-33 for the exact production-constructor shape of each target)
**Apply to:** `NflApiService`, `ExternalApiService`, `ApiFootballService`, `BallDontLieService`
```java
// Package-private test constructor — accepts pre-built RestClient instances.
// Used by NbaApiServiceTest so we can inject mock clients without starting a real HTTP server.
// Never called by Spring — only by unit tests in the same package.
NbaApiService(RestClient restClient, RestClient standingsClient, RestClient statsClient) {
    this.restClient      = restClient;
    this.standingsClient = standingsClient;
    this.statsClient     = statsClient;
}
```
**Exact shape per target** (field counts confirmed by direct source read):
- `NflApiService(RestClient restClient, RestClient standingsClient, RestClient statsClient)` — 3 fields, matches `NbaApiService` exactly.
- `ExternalApiService(RestClient restClient, LeagueRepository leagueRepository)` — 1 RestClient + the repo (already `@Autowired`-friendly, but no non-`@Value` production ctor exists, so it needs a test ctor too).
- `ApiFootballService(RestClient restClient)` — 1 field.
- `BallDontLieService(RestClient restClient)` — 1 field, plus the record-widening edit (see Pitfall 4 in RESEARCH.md).

Each addition needs a junior-dev inline comment per CLAUDE.md, matching the style already on `NbaApiService`'s own test constructor.

### Deep-stub RestClient mocking
**Source:** `src/test/java/com/onestopsports/service/NbaApiServiceTest.java` lines 41-58 (class-level `@Mock(answer = Answers.RETURNS_DEEP_STUBS) RestClient` fields)
**Apply to:** All four external-API-adapter test classes.

### Soft-fail contract (RestClientException → empty/null/Optional.empty, never a thrown exception)
**Source:** `NbaApiServiceTest.java` lines 198-210 (`fetchStandings_apiException_returnsEmptyListWithoutThrowing`)
**Apply to:** Every method on `NflApiService`, `ExternalApiService`, `ApiFootballService`, `BallDontLieService` that wraps a `RestClientException` — this is the contract phase criterion HARD-01 exists to lock in.

### Primitive-arg Mockito matcher gotcha (`anyInt()`/`anyLong()`, never bare `any()`)
**Source:** `src/test/java/com/onestopsports/service/MatchServiceTest.java` line 110 (inline comment: `// fetchMatchDtosByCompetition takes int (primitive) — use anyInt() to avoid NPE from auto-unboxing null`)
**Apply to:** `ExternalApiService.fetchMatchDtosByCompetition(int, LocalDate)`, `ApiFootballService.fetchPlayerStats(int, int)` / `searchPlayerId(String, int, int)`.

### `ResponseStatusException` 404-on-not-found idiom
**Source:** `src/main/java/com/onestopsports/service/SportService.java` line 37 / `src/main/java/com/onestopsports/service/PlayerService.java` lines 48, 66, 96
**Apply to:** `UserServiceTest`, `SportServiceTest`, `PlayerServiceTest` — assert via AssertJ `assertThatThrownBy(...).isInstanceOf(ResponseStatusException.class)` when the repository returns `Optional.empty()`.

### Plain `@InjectMocks`/`@Mock` for non-RestClient services
**Source:** `src/test/java/com/onestopsports/service/MatchServiceTest.java` lines 43-56
**Apply to:** `UserServiceTest`, `SportServiceTest`, `PlayerServiceTest` (no test constructor needed — production constructors already accept pure Spring-bean types).

### AssertJ-only assertion convention
**Source:** used exclusively across all 9 existing test classes (no JUnit `assertEquals` anywhere in the repo)
**Apply to:** all new test files, no exceptions.

## No Analog Found

None — every file targeted by this phase has a strong, directly-verified analog already in the codebase. This phase is explicitly "replicate the existing 3-generation-deep test convention," not "invent a new pattern," per RESEARCH.md's own framing.

## Metadata

**Analog search scope:** `src/test/java/com/onestopsports/{service,controller}/`, `src/main/java/com/onestopsports/{service,controller}/`
**Files scanned:** `NbaApiServiceTest.java`, `MatchServiceTest.java`, `TeamServiceTest.java` (grep-confirmed only), `PlayerServiceCareerStatsTest.java` (read via RESEARCH.md excerpts + direct grep), `AuthControllerTest.java`, `NflApiService.java`, `ExternalApiService.java`, `ApiFootballService.java`, `BallDontLieService.java`, `UserService.java`, `SportService.java`, `PlayerService.java`, `GlobalExceptionHandler.java`
**Pattern extraction date:** 2026-07-08
