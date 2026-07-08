# Phase 1: Backend Service Test Coverage - Research

**Researched:** 2026-07-08
**Domain:** JUnit 5 / Mockito unit testing for Spring 6 `RestClient`-based service adapters and Spring MVC exception handling, in an existing Java 21 / Spring Boot 3.4.4 codebase
**Confidence:** HIGH (all findings are direct reads of this repo's existing source and test code — no external library research was needed for this phase)

## Summary

This phase adds unit tests for the last seven untested backend components (`NflApiService`, `ExternalApiService`, `ApiFootballService`, `BallDontLieService`, `UserService`, `SportService`, `GlobalExceptionHandler`) plus three untested `PlayerService` code paths (`resolvePhotoUrl`, `toDto`, `searchPlayers`). There is no new library to learn: the project already has a fully-formed, three-generation-deep testing convention (`AuthServiceTest`/`MatchServiceTest` → `TeamServiceTest`/`PlayerServiceCareerStatsTest` → `NbaApiServiceTest`) that this phase's tests must simply replicate for the remaining services. `spring-boot-starter-test` (JUnit 5 + Mockito + AssertJ + MockMvc) and `spring-security-test` are already on the test classpath — no new dependency is needed.

The one real landmine is constructor shape: three of the four "no-tests" external-API services (`NflApiService`, `ExternalApiService`, `ApiFootballService`) do **not** yet have the package-private test constructor that `NbaApiService` has (an overload taking pre-built `RestClient` instances directly, used instead of `@InjectMocks` because the production constructor only accepts `@Value` strings). `BallDontLieService` has the same problem. Adding these four small package-private constructors is itself a production-code change this phase must make (not just test code) — the planner should schedule it as an explicit task, and it needs the mandatory junior-dev inline comment per CLAUDE.md since it's new code in an existing file. `UserService`, `SportService`, and the `PlayerService` gaps need no production change at all — pure constructor-injection, straight `@InjectMocks`/`@Mock`, same pattern as `MatchServiceTest`/`TeamServiceTest`.

`GlobalExceptionHandler` is the other point needing care: most of its handler methods can be asserted with a **plain unit call** (`new GlobalExceptionHandler().handleX(ex)` → assert on the returned `ResponseEntity`), but the specific "ResponseStatusException passthrough precedes the Exception catch-all" requirement in the success criteria is actually about **Spring's `@ExceptionHandler` dispatch resolution**, not source-file order — that can only be proven by exercising real Spring MVC dispatch (`MockMvcBuilders.standaloneSetup(...).setControllerAdvice(new GlobalExceptionHandler())`), not by calling the method directly.

**Primary recommendation:** Follow the `NbaApiServiceTest` pattern (package-private test constructor + `@Mock(answer = Answers.RETURNS_DEEP_STUBS) RestClient`) for the four RestClient-based services, adding the missing constructors as a small first task; use plain `@InjectMocks`/`@Mock` (no RestClient involved) for `UserService`/`SportService`/`PlayerService`; and use `MockMvcBuilders.standaloneSetup().setControllerAdvice(...)` for the one `GlobalExceptionHandler` dispatch-order assertion, with plain unit calls for the rest of its handler-method behavior.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| NFL/football/API-Football/balldontlie HTTP adapters (mapping + soft-fail) | API / Backend (service layer) | — | These are Spring `@Service` classes that own external-API mapping logic; tests belong at the same tier, mocking the HTTP boundary (`RestClient`), not the network |
| User favourites CRUD (`UserService`) | API / Backend (service layer) | Database / Storage | Delegates to repositories + reuses `TeamService`/`PlayerService` DTO mappers; tests mock the repositories, not a real DB |
| Sport listing (`SportService`) | API / Backend (service layer) | Database / Storage | Trivial repository-backed service; tests mock `SportRepository` |
| Player photo/DTO/search logic (`PlayerService`) | API / Backend (service layer) | — | Pure mapping + routing logic already partially tested (`PlayerServiceCareerStatsTest`); the remaining gaps are photo-URL derivation and DTO/search mapping, same tier |
| Global error mapping (`GlobalExceptionHandler`) | API / Backend (web/controller layer) | — | `@RestControllerAdvice` is a Spring MVC dispatch concern; correctly verifying it requires exercising the MVC dispatch mechanism, not just calling a method |

This phase does not touch the browser, SSR, CDN, or database tiers — it is scoped entirely to backend service-layer and controller-advice unit tests.

## Standard Stack

### Core (already present — no new dependencies needed)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `spring-boot-starter-test` | 3.4.4 (via parent BOM) | Brings in JUnit 5, Mockito, AssertJ, MockMvc | Already the sole test dependency used by all 9 existing test classes `[VERIFIED: codebase — pom.xml]` |
| `spring-security-test` | 3.4.4-aligned (via BOM) | `SecurityMockMvcRequestPostProcessors.csrf()` for `@WebMvcTest` | Already used by `AuthControllerTest` `[VERIFIED: codebase — pom.xml, AuthControllerTest.java]` |
| Mockito `RETURNS_DEEP_STUBS` | bundled with `spring-boot-starter-test` | Mocks the fluent `RestClient` chain (`get().uri().retrieve().body()`) in one `@Mock` | Established convention in `NbaApiServiceTest`; avoids mocking each intermediate `RequestHeadersUriSpec`/`ResponseSpec` type by hand `[VERIFIED: codebase — NbaApiServiceTest.java]` |
| AssertJ | bundled | `assertThat(...)` fluent assertions | Used exclusively across all existing tests; no JUnit `assertEquals` style in this codebase `[VERIFIED: codebase]` |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `MockMvcBuilders.standaloneSetup(...)` (part of `spring-test`, already on classpath via `spring-boot-starter-test`) | bundled | Exercises real `@ExceptionHandler` dispatch without a full Spring context | Needed specifically for the `GlobalExceptionHandler` "ResponseStatusException precedes catch-all" assertion — see Pitfall 1 below |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `MockMvcBuilders.standaloneSetup()` for GlobalExceptionHandler | `@WebMvcTest` on a throwaway/dummy controller (like `AuthControllerTest`'s pattern) | Also valid and consistent with the existing `AuthControllerTest` convention, but pulls in the full web-slice context (filters, security) for a test that only cares about `@RestControllerAdvice` dispatch order — `standaloneSetup` is lighter and doesn't require `@Import(SecurityConfig.class)` / mocking `JwtUtil` |
| Adding package-private test constructors to `NflApiService`/`ExternalApiService`/`ApiFootballService`/`BallDontLieService` | Reflection-based field injection (e.g. `ReflectionTestUtils.setField`) to swap in a mock `RestClient` on the production-constructed object | Rejected: the codebase has an established, more maintainable convention (test constructor) already in `NbaApiService`; introducing a second technique for four services in the same class of problem would fragment the convention |

**Installation:** None — no new dependencies. All required test infrastructure (JUnit 5, Mockito, AssertJ, MockMvc, spring-security-test) is already declared in `pom.xml`.

**Version verification:** N/A — no new packages are introduced by this phase. `spring-boot-starter-parent` is pinned at `3.4.4`, Java 21, confirmed directly from `pom.xml` `[VERIFIED: codebase — pom.xml lines 9-24]`.

## Package Legitimacy Audit

Not applicable — this phase introduces zero new dependencies. All test tooling already exists in `pom.xml` (`spring-boot-starter-test`, `spring-security-test`) and is exercised by the pre-existing 66-test baseline.

## Architecture Patterns

### System Architecture Diagram

```
                     ┌─────────────────────────────────────────────┐
                     │      New JUnit 5 test classes (this phase)   │
                     │  NflApiServiceTest, ExternalApiServiceTest,  │
                     │  ApiFootballServiceTest, BallDontLieServiceTest,│
                     │  UserServiceTest, SportServiceTest,          │
                     │  GlobalExceptionHandlerTest,                 │
                     │  PlayerServiceTest (photo/toDto/search)      │
                     └───────────────┬───────────────────────────────┘
                                      │ constructs with mocks
                                      ▼
     ┌───────────────────────────────────────────────────────────────────┐
     │                     Service under test (real object)               │
     │  e.g. new NflApiService(mockRestClient, mockStandingsClient,       │
     │                          mockStatsClient)   ← package-private ctor │
     │       new UserService(mockUserRepo, ..., mockTeamService, ...)     │
     │                                              ← @InjectMocks        │
     └───────────────┬─────────────────────────────────────┬─────────────┘
                      │ calls .get().uri().retrieve().body()│ calls repo methods
                      ▼                                     ▼
     ┌────────────────────────────┐         ┌───────────────────────────────┐
     │ Mock RestClient             │         │ Mock *Repository (JpaRepository)│
     │ (RETURNS_DEEP_STUBS)        │         │ (plain Mockito @Mock)           │
     │ — stubs the FINAL .body()   │         │ — stubs findById/findAll/etc.  │
     │   call only; intermediate   │         │                                 │
     │   chain calls (uri/retrieve)│         └───────────────────────────────┘
     │   are auto-stubbed          │
     └────────────────────────────┘
                      │
                      ▼
     ┌───────────────────────────────────────────────────────────────────┐
     │ assertThat(result)... / verify(mock, never())...                   │
     │  — asserts mapped DTO shape + soft-fail (empty list/null/Optional) │
     │  — asserts the RIGHT downstream mock was/wasn't called (routing)   │
     └───────────────────────────────────────────────────────────────────┘

  Separately, for GlobalExceptionHandler's dispatch-order requirement:

     ┌───────────────────┐   throws ResponseStatusException   ┌─────────────────────────┐
     │ Dummy @Controller  │ ───────────────────────────────►   │ MockMvc (standaloneSetup)│
     │ (test-only, throws │                                    │  .setControllerAdvice(   │
     │  the exception      │                                    │     new GlobalException  │
     │  under test)         │                                    │     Handler())            │
     └───────────────────┘                                    └───────────┬─────────────┘
                                                                            │ Spring picks the
                                                                            │ MOST SPECIFIC handler
                                                                            ▼
                                                     handleResponseStatus() is invoked,
                                                     NOT handleGeneric() — asserted via
                                                     .andExpect(status().is(...))
```

### Recommended Project Structure
No new directories — tests land in the existing flat package layout:
```
src/test/java/com/onestopsports/
├── controller/
│   └── GlobalExceptionHandlerTest.java     # NEW — standaloneSetup + direct-call hybrid
├── service/
│   ├── NflApiServiceTest.java              # NEW — mirrors NbaApiServiceTest
│   ├── ExternalApiServiceTest.java         # NEW — mirrors NbaApiServiceTest (+ LeagueRepository mock)
│   ├── ApiFootballServiceTest.java         # NEW — mirrors NbaApiServiceTest
│   ├── BallDontLieServiceTest.java         # NEW — mirrors NbaApiServiceTest
│   ├── UserServiceTest.java                # NEW — plain @InjectMocks, mirrors MatchServiceTest style
│   ├── SportServiceTest.java               # NEW — plain @InjectMocks, simplest of all (1 dependency)
│   └── PlayerServiceTest.java              # NEW — extends coverage beyond PlayerServiceCareerStatsTest
```
`PlayerServiceCareerStatsTest` already exists and covers `getPlayerCareerStats`; a new `PlayerServiceTest` (or an additional test class) should cover `resolvePhotoUrl`, `toDto`, and `searchPlayers` without duplicating the existing career-stats tests. Either a new file or additions to the existing file are acceptable — the existing file is scoped to "CareerStats" in its name, so a **separate `PlayerServiceTest.java`** for the other three methods keeps naming honest (planner's call).

### Pattern 1: RestClient deep-stub testing (RETURNS_DEEP_STUBS)
**What:** Mock the entire `RestClient` fluent chain with one `@Mock(answer = Answers.RETURNS_DEEP_STUBS)` field, then stub only the terminal `.body(SomeResponse.class)` call.
**When to use:** Any service whose production code calls `restClient.get().uri(...).retrieve().body(X.class)`.
**Example:**
```java
// Source: existing repo pattern — src/test/java/com/onestopsports/service/NbaApiServiceTest.java
@Mock(answer = Answers.RETURNS_DEEP_STUBS)
RestClient restClient;

NflApiService nflApiService;

@BeforeEach
void setUp() {
    nflApiService = new NflApiService(restClient, standingsClient, statsClient); // package-private test ctor
}

@Test
void fetchGameDtosByDate_nullApiResponse_returnsEmptyList() {
    when(restClient.get().uri(anyString()).retrieve()
            .body(NflApiService.EspnScoreboardResponse.class))
            .thenReturn(null);

    List<MatchDto> result = nflApiService.fetchGameDtosByDate(LocalDate.of(2025, 9, 7), 8L);

    assertThat(result).isEmpty();
}
```
**Gotcha:** When the production code builds the URI with a templated string that takes a `String` argument (e.g. `.uri("/teams/{id}/roster", espnTeamId)`), the stub matcher must use `any(String.class), any(Object[].class)` (varargs) rather than `anyString()` alone — see `NbaApiServiceTest.fetchPlayersByTeam_nullApiResponse_returnsEmptyList` for the exact form.

### Pattern 2: Package-private test constructor (production-code change required)
**What:** A second, package-private constructor on the service class that accepts pre-built `RestClient` instances directly, bypassing the `@Value`-driven production constructor.
**When to use:** Any `@Service` whose only constructor takes `@Value` strings and builds its own `RestClient` internally — `@InjectMocks` cannot inject a mock `RestClient` into a field that's built inside the constructor body, so a second constructor is the only way to substitute a mock.
**Missing today on:** `NflApiService`, `ExternalApiService`, `ApiFootballService`, `BallDontLieService` — **this phase must add these four constructors as a small production-code task before the corresponding test classes can compile.**
**Example (already present, to be replicated):**
```java
// Source: src/main/java/com/onestopsports/service/NbaApiService.java lines 60-78
@org.springframework.beans.factory.annotation.Autowired
public NbaApiService(
        @Value("${external-api.nba.base-url}") String baseUrl,
        @Value("${external-api.nba.standings-url}") String standingsUrl,
        @Value("${external-api.nba.stats-url}") String statsUrl) {
    this.restClient      = RestClient.builder().baseUrl(baseUrl).build();
    this.standingsClient = RestClient.builder().baseUrl(standingsUrl).build();
    this.statsClient     = RestClient.builder().baseUrl(statsUrl).build();
}

// Package-private test constructor — accepts pre-built RestClient instances.
// Used by NbaApiServiceTest so we can inject mock clients without starting a real HTTP server.
// Never called by Spring — only by unit tests in the same package.
NbaApiService(RestClient restClient, RestClient standingsClient, RestClient statsClient) {
    this.restClient      = restClient;
    this.standingsClient = standingsClient;
    this.statsClient     = statsClient;
}
```
**Applying this to each target (exact shape needed):**
- `NflApiService` — needs `NflApiService(RestClient restClient, RestClient standingsClient, RestClient statsClient)`, mirroring `NbaApiService` exactly (same 3-client shape, confirmed by reading `NflApiService`'s production constructor — 3 `@Value`-injected URLs, 3 `RestClient` fields).
- `ExternalApiService` — its production constructor takes `baseUrl`, `apiKey`, and `LeagueRepository`. The test constructor should be `ExternalApiService(RestClient restClient, LeagueRepository leagueRepository)` — a single `RestClient` (there is only one client in this service) plus the repository (which is already easily mockable and doesn't need a test-constructor workaround — `@InjectMocks`-style manual construction works for it directly, but since the class only has one non-test constructor and it doesn't accept a `RestClient` param, a test constructor is still needed for the `RestClient` alone).
- `ApiFootballService` — needs `ApiFootballService(RestClient restClient)` — its production constructor takes `baseUrl` + `apiKey` only, one client.
- `BallDontLieService` — needs `BallDontLieService(RestClient restClient)` — same shape, one client, `baseUrl` + `apiKey` in production.

Each new constructor requires the same "why this exists" inline comment style as `NbaApiService`'s, per CLAUDE.md's mandatory junior-dev-comment rule for new code.

### Pattern 3: Plain `@InjectMocks` for non-RestClient services
**What:** Standard Mockito `@Mock` fields for each repository/service dependency + `@InjectMocks` on the service under test — no test constructor needed because the production constructor already accepts pure Spring-bean types.
**When to use:** `UserService`, `SportService`, and the `PlayerService` gaps (`resolvePhotoUrl`/`toDto`/`searchPlayers`) — all already take `@Autowired`-friendly constructor params (repositories, other services) with no `@Value` primitives to route around.
**Example:**
```java
// Mirrors src/test/java/com/onestopsports/service/MatchServiceTest.java and TeamServiceTest.java
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
**Gotcha for `SportService`:** it has exactly one dependency (`SportRepository`) — the simplest test class in this phase, essentially the same shape as `AuthServiceTest`'s style but with fewer mocks.

### Anti-Patterns to Avoid
- **Calling `GlobalExceptionHandler`'s methods directly to "prove" dispatch order:** this only proves the method's own logic (status code, body shape) — it says nothing about whether Spring's real `@ExceptionHandler` resolver would have picked that method over `handleGeneric()` for a given thrown exception. The "passthrough precedes catch-all" success criterion requires exercising real MVC dispatch (see Pitfall 1).
- **Reaching for `anyString()` when the RestClient call uses a `.uri(String, Object...)` varargs overload:** causes a `NullPointerException`/mismatched-stub failure or (worse) a silently-unstubbed call falling through to `null`. Use `any(String.class), any(Object[].class)` as `NbaApiServiceTest` does.
- **Skipping the primitive-arg `anyInt()`/`anyLong()` matchers when a method takes `int`/`long` (not `Integer`/`Long`):** `any()` alone will NPE on auto-unboxing of `null` for a primitive parameter. `MatchServiceTest`'s comment on `externalApiService.fetchMatchDtosByCompetition(anyInt(), any())` is the exact precedent — `ApiFootballService.fetchPlayerStats(int, int)` / `searchPlayerId(String, int, int)` have the same shape and need the same treatment.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Mocking a fluent HTTP client chain | Custom fake/stub implementing `RestClient`'s interfaces | Mockito `@Mock(answer = Answers.RETURNS_DEEP_STUBS)` | Already the established, working pattern in this codebase (`NbaApiServiceTest`); a hand-rolled fake would duplicate effort and diverge from convention |
| Verifying `@RestControllerAdvice` exception-handler precedence | Manually re-implementing Spring's handler-resolution logic in a test assertion (e.g. reflectively inspecting method order) | `MockMvcBuilders.standaloneSetup(dummyController).setControllerAdvice(new GlobalExceptionHandler())` | Uses Spring's real `ExceptionHandlerExceptionResolver` — the only way to actually prove dispatch behavior rather than assume it |
| Building HTTP response fixtures for ESPN/API-Football/balldontlie JSON | Hand-typed JSON strings deserialized ad hoc | Java record literals matching the service's own nested `record` types (as `NbaApiServiceTest`'s `scoreboardWith(...)`/`standingsWithTwo(...)` helpers do) | The response records already exist as inner types on each service (`NflApiService.EspnScoreboardResponse`, `ApiFootballService.ApiResponse`, `BallDontLieService`'s private `BdlPlayersResponse`/`BdlPlayer`); constructing them directly is simpler and type-safe compared to JSON string fixtures |

**Key insight:** every "don't hand-roll" item in this phase is really "don't diverge from the pattern the codebase already established for exactly this problem" — there is no new library or framework decision to make.

## Common Pitfalls

### Pitfall 1: GlobalExceptionHandler's precedence claim is a dispatch-order fact, not a source-order fact
**What goes wrong:** A test that calls `handler.handleResponseStatus(ex)` directly and asserts the returned `ResponseEntity` proves the method's *own* logic works, but proves nothing about whether Spring would actually route a thrown `ResponseStatusException` to that method instead of `handleGeneric()`.
**Why it happens:** Spring's `ExceptionHandlerMethodResolver` picks the handler whose declared exception type has the smallest "distance" in the exception's class hierarchy from the thrown exception — this is independent of the order methods appear in the source file. `ResponseStatusException` (thrown by `UserService`/`SportService`/`AuthService`) is a subtype of `RuntimeException`/`Exception`, so both `handleResponseStatus(ResponseStatusException)` and `handleGeneric(Exception)` are structurally "candidates"; Spring's specificity-based resolution (not declaration order) is what guarantees the more specific handler wins.
**How to avoid:** Use `MockMvcBuilders.standaloneSetup(new DummyThrowingController()).setControllerAdvice(new GlobalExceptionHandler()).build()`, have the dummy controller's endpoint throw a `ResponseStatusException(HttpStatus.NOT_FOUND, "x")`, and assert `.andExpect(status().isNotFound())` — this exercises real dispatch. A parallel test throwing a raw unchecked exception (e.g. `RuntimeException`) from the same dummy endpoint should assert `.andExpect(status().isInternalServerError())` to prove the catch-all still works for genuinely unhandled cases.
**Warning signs:** A `GlobalExceptionHandlerTest` that only ever instantiates `new GlobalExceptionHandler()` and calls its methods with `new SomeException(...)` as a plain Java object, never going through MockMvc/a controller — that test cannot fail even if someone accidentally reorders or misconfigures the handler methods, so it isn't actually testing the documented behavior.

### Pitfall 2: Missing package-private test constructors will show up as a compile error, not a test failure
**What goes wrong:** Writing `new NflApiService(mockRestClient, mockStandingsClient, mockStatsClient)` in a test today fails to compile, because `NflApiService` only has the public `@Value`-based constructor — there's no ambiguity error, just "constructor NflApiService in class NflApiService cannot be applied to given types."
**Why it happens:** Only `NbaApiService` received this treatment when it was built; `NflApiService`, `ExternalApiService`, `ApiFootballService`, and `BallDontLieService` were added later/earlier without it (confirmed by reading all four service source files — none have a second constructor).
**How to avoid:** Treat "add the missing package-private test constructor" as an explicit, small production-code task per service (4 total), done *before* or *alongside* writing that service's test class — not an incidental side effect discovered mid-test-writing. Each addition needs the mandatory junior-dev inline comment (CLAUDE.md hard rule) explaining why the second constructor exists.
**Warning signs:** A plan that lists only "write NflApiServiceTest.java" as a task, with no companion task/subtask to touch `NflApiService.java` itself, will hit a compile wall immediately.

### Pitfall 3: `@Value` defaults obscure which properties genuinely need a value at test-construction time
**What goes wrong:** `NbaApiService`/`ApiFootballService`/`BallDontLieService`'s production constructors take `@Value` strings with **no default** (`NbaApiService`, `ApiFootballService`) or **inline defaults** (`BallDontLieService`'s `${external-api.balldontlie.base-url:https://api.balldontlie.io/v1}`, `NflApiService`'s `standings-url`/`stats-url` params). None of this matters for the test-constructor path (it never reads `@Value` at all), but a developer might mistakenly try to `@SpringBootTest`-load these services instead of unit-testing them, which pulls in `application-test.yml`'s config and could produce confusing "property not found" errors if a key isn't set there.
**Why it happens:** Mixing up "does this component need a Spring context" with "does this component need mocked collaborators" — this phase is about the latter, never the former.
**How to avoid:** Always use `@ExtendWith(MockitoExtension.class)` (no `@SpringBootTest`) for every new test class in this phase, exactly as all nine existing service-layer test classes do.
**Warning signs:** Any new test class importing `@SpringBootTest` or `@ActiveProfiles("test")` — none of the Phase 1 targets need a Spring context.

### Pitfall 4: BallDontLieService's inner records are `private`, not `public`
**What goes wrong:** `BdlPlayersResponse`/`BdlPlayer` are declared `private record` inside `BallDontLieService`. Unlike `NbaApiService`/`NflApiService`/`ApiFootballService` (whose response records are `public record`), a test in the same package **cannot reference these types directly** to build a fixture response object (e.g. `new BallDontLieService.BdlPlayersResponse(...)` will not compile from another class).
**Why it happens:** Author's choice — the class-level comment even says "These are private — no other class needs to know about the external API shape."
**How to avoid:** The test must stub the mocked `RestClient`'s final `.body(...)` call to return the deserialized object using the RestClient mock's return type — but because the return type itself is `private`, `when(restClient...body(BallDontLieService.BdlPlayersResponse.class)).thenReturn(???)` cannot be constructed from the test class. Two options for the planner to choose between: (a) widen the records to package-private (drop `private`, keep them out of the public API surface) — the minimal, most consistent-with-existing-pattern fix, matching how `NbaApiService`'s `EspnSummaryResponse` family is package-private (not `private`) specifically so tests/other code in the same package can reference it (see that class's own comment: "IMPORTANT: These must be package-private (not `private`)... blocks Jackson from deserializing"); or (b) build the response with real JSON strings and don't use `RETURNS_DEEP_STUBS`/typed stubbing at all, instead using a Jackson `ObjectMapper` reference to construct the object indirectly. **Recommended: (a)** — change `private record` to package-private `record` (drop the `private` modifier) on `BdlPlayersResponse`/`BdlPlayer`, matching the rest of the codebase's convention and enabling direct construction in the test, exactly like `NbaApiService`'s note about why `private record` breaks things.
**Warning signs:** A `BallDontLieServiceTest` that compiles by accident because it never tries to stub the `.body(...)` call with a concrete object (i.e., it only tests exception paths) — this would under-cover the happy-path mapping requirement in Success Criterion 1.

### Pitfall 5: DataLoader makes a real (caught) network call at `OneStopSportsApplicationTests` context-load time — pre-existing, out of scope
**What goes wrong:** A developer noticing that `mvn test` triggers a live outbound HTTP attempt to `football-data.org` (via `DataLoader.run() → ExternalApiService.fetchTeamsByCompetition(...)`) during the existing `OneStopSportsApplicationTests.contextLoads()` test might conclude Phase 1 needs to "fix" this to satisfy "no live network calls."
**Why it happens:** `DataLoader` is a `CommandLineRunner` with no profile guard; it runs on every `@SpringBootTest` context load, including in the `test` profile (H2 + Flyway off). It DOES catch `RestClientResponseException`/`Exception` broadly (confirmed by reading `DataLoader.run()`), so the test doesn't fail even if the placeholder API key gets rejected or the network is unavailable — it's just slow/flaky-adjacent, not a hard failure.
**How to avoid:** This is pre-existing behavior in the already-green 66-test baseline, unrelated to the four NEW test classes and three PlayerService methods this phase targets. The "no live network calls" success criterion (Success Criterion 1) applies specifically to the *new* unit tests being written (`NflApiServiceTest`, etc.), which use mocked `RestClient`s and therefore genuinely make zero network calls. Do not scope a fix for `DataLoader`'s startup behavior into this phase — flag it as a pre-existing quirk only if CI reliability becomes a visible blocker, otherwise leave untouched.
**Warning signs:** A plan that adds a task to "disable DataLoader in tests" — that's scope creep beyond HARD-01 and isn't listed in the phase's success criteria.

## Code Examples

### Deep-stub RestClient pattern (verbatim precedent for this phase's new services)
```java
// Source: src/test/java/com/onestopsports/service/NbaApiServiceTest.java (existing, in-repo)
@SuppressWarnings({"unchecked", "rawtypes"})
@ExtendWith(MockitoExtension.class)
class NbaApiServiceTest {
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    RestClient restClient;

    NbaApiService nbaApiService;

    @BeforeEach
    void setUp() {
        nbaApiService = new NbaApiService(restClient, standingsClient, statsClient);
    }

    @Test
    void fetchGameDtosByDate_nullApiResponse_returnsEmptyList() {
        when(restClient.get().uri(anyString()).retrieve()
                .body(NbaApiService.EspnScoreboardResponse.class))
                .thenReturn(null);

        List<MatchDto> result = nbaApiService.fetchGameDtosByDate(LocalDate.of(2025, 4, 20), 7L);

        assertThat(result).isEmpty();
    }
}
```

### RestClientException soft-fail pattern
```java
// Source: src/test/java/com/onestopsports/service/NbaApiServiceTest.java — apply the same shape
// to NflApiService.fetchStandings, ExternalApiService.fetchStandings,
// ApiFootballService.searchPlayerId/fetchPlayerStats, and BallDontLieService.searchPlayerByName
// (each swallows a broad Exception/RestClientException and returns empty/null/Optional.empty)
@Test
void fetchStandings_apiException_returnsEmptyListWithoutThrowing() {
    when(standingsClient.get().uri(anyString()).retrieve()
            .body(NbaApiService.EspnStandingsResponse.class))
            .thenThrow(new RestClientException("ESPN standings unavailable"));

    List<StandingsEntryDto> result = nbaApiService.fetchStandings(7L);

    assertThat(result).isEmpty();
}
```

### Routing-verification pattern (verify a wrong-sport service is never called)
```java
// Source: src/test/java/com/onestopsports/service/PlayerServiceCareerStatsTest.java
// Apply the same never()-verification idiom for PlayerService.resolvePhotoUrl's
// sport-slug switch (basketball -> ESPN NBA CDN, american-football -> ESPN NFL CDN, else -> null)
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

### GlobalExceptionHandler — recommended standaloneSetup dispatch test (NEW pattern for this phase)
```java
// NEW pattern — no direct precedent in the repo yet, but uses only classes already
// on the test classpath (spring-boot-starter-test's spring-test module).
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

## State of the Art

Not applicable to this phase in the traditional sense — this is not adopting a new framework or replacing a deprecated approach. The only "before/after" is intra-project:

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|---------------|--------|
| No test-constructor overload on `NflApiService`/`ExternalApiService`/`ApiFootballService`/`BallDontLieService` | Package-private test constructor added (this phase) | Phase 1 | Unlocks deep-stub RestClient testing for the 4 previously-untestable services |
| `BallDontLieService`'s response records `private` | Widen to package-private (this phase, recommended) | Phase 1 | Unlocks direct fixture construction from the test class in the same package |

**Deprecated/outdated:** None — no libraries or APIs used by this phase are deprecated.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Widening `BallDontLieService`'s `BdlPlayersResponse`/`BdlPlayer` from `private record` to package-private is the preferred fix (vs. an alternative JSON-based test strategy) | Common Pitfalls, Pitfall 4 | Low — this is a design choice within the planner's discretion; both options work, this is a recommendation, not a hard fact about the codebase requiring user confirmation |
| A2 | A single `NflApiService(RestClient, RestClient, RestClient)` test constructor matching `NbaApiService`'s 3-arg shape is sufficient (rather than 2 or 4 args) | Architecture Patterns, Pattern 2 | Low — directly confirmed by reading `NflApiService`'s production constructor (3 `@Value` params, 3 `RestClient` fields), so this is effectively `[VERIFIED: codebase]` not really assumed, but flagged since it's a planner decision about exact signature |

**This table is nearly empty by design:** the overwhelming majority of this research is `[VERIFIED: codebase]` — direct reads of the actual source files and existing test classes in this repository, not external documentation or training-data claims. No user confirmation is needed before planning; the two items above are planner-discretion notes, not open factual risks.

## Open Questions

1. **Should `PlayerServiceCareerStatsTest.java` be extended, or should a new `PlayerServiceTest.java` be created for `resolvePhotoUrl`/`toDto`/`searchPlayers`?**
   - What we know: `PlayerServiceCareerStatsTest` exists today and is scoped (by name and by its own header comment) purely to `getPlayerCareerStats` routing.
   - What's unclear: whether the planner prefers one `PlayerServiceTest` class covering everything untested on `PlayerService`, or keeping `resolvePhotoUrl`/`toDto`/`searchPlayers` in a differently-named file to avoid renaming the existing (working, unmodified) `PlayerServiceCareerStatsTest`.
   - Recommendation: create a new `PlayerServiceTest.java` (leave `PlayerServiceCareerStatsTest.java` untouched) — this avoids touching/renaming a file with zero incremental risk and keeps the "CareerStats"-scoped name honest.

2. **Does `resolvePhotoUrl` need a `Player`/`Team`/`Sport` entity builder helper distinct from `PlayerServiceCareerStatsTest.playerInSport(...)`?**
   - What we know: `PlayerServiceCareerStatsTest.playerInSport(...)` already builds a `Player → Team → Sport` (+ League) chain that's reusable for `resolvePhotoUrl`'s three-layer logic (persisted `photoUrl` → ESPN CDN via `externalId` + `sport.slug` → null).
   - What's unclear: whether to extract this helper to a shared test-fixture utility class, or duplicate a smaller version privately in the new `PlayerServiceTest`.
   - Recommendation: duplicate a minimal private helper in the new test class (existing codebase convention — no shared test-fixture utility class exists anywhere in `src/test/`, each test class builds its own private helpers) rather than introducing a new shared-fixtures pattern for one method.

## Environment Availability

Skipped — this phase has no external tool/service dependencies beyond the JDK/Maven toolchain already required to build the project (Java 21, Maven, `mvn test` running against H2 in-memory — no Postgres/Redis/Docker needed for these unit tests). `spring-boot-starter-test` and `spring-security-test` are already declared dependencies; nothing new to provision.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 (via `spring-boot-starter-test`, Spring Boot 3.4.4 BOM) + Mockito + AssertJ |
| Config file | None dedicated — driven by `pom.xml`'s `spring-boot-starter-test` dependency; `src/test/resources/application-test.yml` only matters for `@SpringBootTest`-based tests (not used by any Phase 1 target) |
| Quick run command | `mvn test -Dtest=NflApiServiceTest` (swap class name per new test) |
| Full suite command | `mvn test` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| HARD-01 (Criterion 1) | `NflApiService`/`ExternalApiService`/`ApiFootballService`/`BallDontLieService` happy-path mapping + soft-fail | unit | `mvn test -Dtest=NflApiServiceTest,ExternalApiServiceTest,ApiFootballServiceTest,BallDontLieServiceTest` | ❌ Wave 0 — all 4 files new; 4 production constructors also missing (Pitfall 2) |
| HARD-01 (Criterion 2) | `UserService`/`SportService`/`GlobalExceptionHandler` unit tests incl. dispatch-order | unit | `mvn test -Dtest=UserServiceTest,SportServiceTest,GlobalExceptionHandlerTest` (or `GlobalExceptionHandlerDispatchTest`) | ❌ Wave 0 — all 3 files new |
| HARD-01 (Criterion 3) | `PlayerService.resolvePhotoUrl`/`toDto`/`searchPlayers` unit tests | unit | `mvn test -Dtest=PlayerServiceTest` | ❌ Wave 0 — new file (or new methods in an existing one, see Open Question 1) |
| HARD-01 (Criterion 4) | Full 66+N suite green, no regressions | unit (full suite) | `mvn test` | ✅ — existing 66 tests already pass; this criterion is validated by running the full suite after all new tests land |

### Sampling Rate
- **Per task commit:** `mvn test -Dtest=<ClassUnderTest>` for the class just written
- **Per wave merge:** `mvn test` (full suite)
- **Phase gate:** Full suite green (66 pre-existing + all new tests) before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `NflApiService.java` — add package-private test constructor `NflApiService(RestClient, RestClient, RestClient)` (production-code change, needed before `NflApiServiceTest` can compile)
- [ ] `ExternalApiService.java` — add package-private test constructor `ExternalApiService(RestClient, LeagueRepository)` (production-code change)
- [ ] `ApiFootballService.java` — add package-private test constructor `ApiFootballService(RestClient)` (production-code change)
- [ ] `BallDontLieService.java` — add package-private test constructor `BallDontLieService(RestClient)`, AND widen `BdlPlayersResponse`/`BdlPlayer` from `private record` to package-private `record` (two production-code changes, see Pitfall 4)
- [ ] `src/test/java/com/onestopsports/service/NflApiServiceTest.java` — new
- [ ] `src/test/java/com/onestopsports/service/ExternalApiServiceTest.java` — new
- [ ] `src/test/java/com/onestopsports/service/ApiFootballServiceTest.java` — new
- [ ] `src/test/java/com/onestopsports/service/BallDontLieServiceTest.java` — new
- [ ] `src/test/java/com/onestopsports/service/UserServiceTest.java` — new
- [ ] `src/test/java/com/onestopsports/service/SportServiceTest.java` — new
- [ ] `src/test/java/com/onestopsports/service/PlayerServiceTest.java` — new (see Open Question 1)
- [ ] `src/test/java/com/onestopsports/controller/GlobalExceptionHandlerTest.java` (or split into a dedicated `...DispatchTest`) — new, needs the `standaloneSetup` pattern for the passthrough-ordering assertion (Pitfall 1)

*(No shared test-fixture framework work is needed — the existing per-class private-helper convention, e.g. `NbaApiServiceTest.scoreboardWith(...)`, is sufficient and should be followed, not replaced.)*

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-------------------|
| V2 Authentication | No | This phase adds unit tests only; no new authentication surface. (`AuthServiceTest`/`AuthControllerTest` already cover V2 in the existing baseline.) |
| V3 Session Management | No | Not touched by this phase. |
| V4 Access Control | No | `UserService`'s favourite-CRUD tests validate *behavior* (404 on missing user/team/player, skip-if-already-favourited), not access control — access control (JWT-guarding `/api/users/me/**`) is already covered by existing `SecurityConfig` matcher-order behavior and is out of this phase's scope. |
| V5 Input Validation | No new surface | This phase tests existing mapping/routing logic; it does not add new request-validation code. `GlobalExceptionHandler`'s existing `@Valid`/`MethodArgumentNotValidException` handling is already tested by `AuthControllerTest` and is not re-scoped here. |
| V6 Cryptography | No | Not touched. |
| V7 Error Handling & Logging (ASVS 4.0 renumbering; V7 in some ASVS versions) | Yes | `GlobalExceptionHandler`'s catch-all (`handleGeneric`) already follows the standard control of logging full detail server-side (`log.error(..., ex)`) while returning a generic client-facing message ("An unexpected error occurred") — this phase's new tests should assert this behavior is preserved, not weakened, per the existing pattern. |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|----------------------|
| Information disclosure via verbose error messages | Information Disclosure | `GlobalExceptionHandler.handleGeneric` already returns a generic message and logs detail server-side only — the new dispatch test (Pitfall 1) should explicitly assert the client-facing body does NOT leak the raw exception message/stack trace for the catch-all path, reinforcing this existing control rather than introducing a new one |
| Silent external-API failures masking as empty results (soft-fail) | Denial of Service (partial) | Already the established pattern (`RestClientException` → empty list/null/`Optional.empty()`) across every external-API service in this codebase; this phase's tests exist specifically to lock in that contract so a future refactor can't accidentally let an exception propagate to a 500 |

No new attack surface is introduced by this phase — it is exclusively unit-test coverage of existing, already-shipped, already-mocked-at-the-boundary logic. `security_enforcement` findings here are about *preserving* existing controls via regression tests, not adding new ones.

## Sources

### Primary (HIGH confidence — direct codebase reads, this session)
- `pom.xml` — confirmed Java 21, Spring Boot 3.4.4 parent, `spring-boot-starter-test` + `spring-security-test` as the only test dependencies, no Mockito/JUnit/AssertJ version pinned beyond the Boot BOM
- `src/test/java/com/onestopsports/service/NbaApiServiceTest.java` — the canonical RETURNS_DEEP_STUBS + package-private test constructor pattern this phase must replicate
- `src/test/java/com/onestopsports/service/MatchServiceTest.java`, `TeamServiceTest.java`, `AuthServiceTest.java`, `PlayerServiceCareerStatsTest.java` — plain `@InjectMocks` conventions, `anyInt()` primitive gotcha, routing-verification idiom
- `src/test/java/com/onestopsports/controller/AuthControllerTest.java` — `@WebMvcTest` + `@Import(SecurityConfig.class)` + `excludeAutoConfiguration` + `spring-security-test`'s `csrf()` pattern
- `src/test/java/com/onestopsports/OneStopSportsApplicationTests.java` — confirms `@MockBean RedisConnectionFactory` requirement for any future `@SpringBootTest` (not needed by this phase's targets, but documented for completeness)
- `src/test/resources/application-test.yml` — H2 + Flyway-off + Redis-none test profile config
- `src/main/java/com/onestopsports/service/NflApiService.java`, `ExternalApiService.java`, `ApiFootballService.java`, `BallDontLieService.java`, `UserService.java`, `SportService.java`, `PlayerService.java`, `NbaApiService.java` (constructor comparison) — actual method signatures, mapping logic, and exact soft-fail returns
- `src/main/java/com/onestopsports/controller/GlobalExceptionHandler.java` — all 9 exception handlers and their exact status-code mappings
- `src/main/java/com/onestopsports/config/DataLoader.java` — confirmed the pre-existing, caught, out-of-scope live-network-attempt behavior at context-load time
- `src/main/resources/application.yml` — confirmed `@Value` property keys/defaults for all four external-API services
- `src/main/java/com/onestopsports/model/Team.java` — confirmed `addLeague`/`getPrimaryLeague` semantics (used by existing `PlayerServiceCareerStatsTest`/`TeamServiceTest` fixture helpers, relevant to any new `PlayerServiceTest` fixtures)
- `.planning/REQUIREMENTS.md`, `.planning/ROADMAP.md`, `.planning/STATE.md`, `.planning/config.json` — HARD-01 requirement text, Phase 1 success criteria, decision log, `nyquist_validation`/`security_enforcement` toggles (both enabled by default)

### Secondary (MEDIUM confidence)
None used — no web/external documentation was needed for this phase; every claim is directly grounded in this repository's own source and test code.

### Tertiary (LOW confidence)
None.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new libraries; existing `pom.xml` dependencies read directly
- Architecture: HIGH — existing test/production code read directly; patterns are precedent, not inference
- Pitfalls: HIGH for Pitfalls 1-3 and 5 (directly confirmed via source reads); HIGH for Pitfall 4 (directly confirmed `private record` in `BallDontLieService.java`, recommendation itself is a planner-discretion call, logged in Assumptions)

**Research date:** 2026-07-08
**Valid until:** No expiry driver — this research is scoped to the current state of this specific repository's source code, not to an external library's release cadence. Re-research only if the target service files change materially before planning executes.
