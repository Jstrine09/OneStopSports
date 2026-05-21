# Testing Patterns

**Analysis Date:** 2026-05-21

## Test Framework

**Runner:**
- JUnit 5 (`org.junit.jupiter`) — invoked via Surefire from `mvn test`
- Mockito 5 (`org.mockito.junit.jupiter.MockitoExtension`)
- AssertJ (`org.assertj.core.api.Assertions`)
- Spring Boot Test (`spring-boot-starter-test`) + `spring-security-test`
- H2 in-memory database for any test that touches the Spring context

**Run commands:**
```bash
mvn test                          # Run all tests
mvn test -Dtest=NbaApiServiceTest # Run a single class
```

**Total: 57 tests, all passing.**

## Test File Organization

**Location:** `src/test/java/com/onestopsports/` — mirrors the production layout.

```
src/test/
├── java/com/onestopsports/
│   ├── OneStopSportsApplicationTests.java   Context-load smoke test
│   ├── controller/
│   │   └── AuthControllerTest.java          @WebMvcTest slice test
│   └── service/
│       ├── AuthServiceTest.java             Pure unit test
│       ├── LeagueServiceTest.java           Pure unit test
│       ├── MatchServiceTest.java            Pure unit test
│       ├── NbaApiServiceTest.java           Pure unit test (RestClient mocks)
│       └── PlayerServiceCareerStatsTest.java Pure unit test (multi-sport routing)
└── resources/
    └── application-test.yml                 Test profile config (no Redis)
```

**Naming:** `<ClassUnderTest>Test.java` (e.g. `AuthService` → `AuthServiceTest`). One narrow slice exception: `PlayerServiceCareerStatsTest` covers only the career-stats router in `PlayerService` — focused name communicates the scope.

## Test Class Inventory

| Test class | Type | Test count | What it covers |
|---|---|---|---|
| `AuthServiceTest` | Pure unit (Mockito) | 6 | register success / duplicate username / duplicate email; login success / wrong password; `loadUserByUsername` |
| `AuthControllerTest` | `@WebMvcTest` slice | 7 | POST `/auth/register` happy + validation failures (blank username, invalid email, short password); POST `/auth/login` happy / 401 / 400 |
| `MatchServiceTest` | Pure unit (Mockito) | 13 | null/null guard clauses; unknown leagueId; basketball/american-football/football routing; football with/without externalId; `getMatchById` (null + valid); `getMatchEvents` (null + valid); `getMatchStats`/`getMatchLineups` return empty maps |
| `NbaApiServiceTest` | Pure unit (deep-stub RestClient) | 12 | null scoreboard response; STATUS_FINAL → FINISHED; STATUS_IN_PROGRESS → LIVE; SCHEDULED → null scores; leagueId propagation; standings sorted by wins desc; standings empty on API exception; basketball has no draws; played = wins + losses |
| `LeagueServiceTest` | Pure unit (Mockito) | 9 | `getStandings` 404 on unknown league; basketball/american-football/football routing; football without externalId; `getLeagueById` (found / not found); `getLeaguesBySport` (with / without leagues) |
| `PlayerServiceCareerStatsTest` | Pure unit (Mockito) | 9 | 404 on unknown player; NBA routing (with / without externalId); NFL routing; football with externalId (skip search); football without externalId (search + persist); football search miss; football unknown league; unsupported sport falls through |
| `OneStopSportsApplicationTests` | `@SpringBootTest` context-load | 1 | Verifies the full Spring context boots cleanly under the `test` profile |

**Total: 57 tests.**

## Pure Unit Test Pattern

The dominant pattern across this codebase. No Spring context, no database, no HTTP.

**Standard skeleton:**
```java
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() { ... }

    @Test
    void register_success_returnsTokenAndUsername() {
        // GIVEN
        when(userRepository.findByUsername("james")).thenReturn(Optional.empty());
        // ...

        // WHEN
        AuthResponse response = authService.register(registerRequest);

        // THEN
        assertThat(response.token()).isEqualTo("fake.jwt.token");
        verify(userRepository).save(any(UserAccount.class));
    }
}
```

**Shared conventions across all pure unit tests:**

1. **`@ExtendWith(MockitoExtension.class)`** at the class level.
2. **`@Mock` on every dependency.** `@InjectMocks` on the class under test — Mockito picks the matching constructor automatically.
3. **GIVEN / WHEN / THEN comments** structure every test body.
4. **AssertJ for assertions** — `assertThat(x).isEqualTo(y)`, `assertThat(list).hasSize(2)`, `assertThat(list).containsExactly(...)`, `assertThatThrownBy(...)`. Never JUnit's bare `assertEquals`.
5. **`verify(mock, never()).method(any())`** is used aggressively to prove the routing logic — e.g. `MatchServiceTest` proves a basketball league never calls `ExternalApiService` and vice-versa.
6. **Test method naming:** `<method>_<scenario>_<expectedOutcome>` (e.g. `register_duplicateUsername_throwsConflict`, `fetchGameDtosByDate_finalGame_mapsStatusToFinished`).
7. **Section dividers:** `// ── getStandings — routing ─────` matches the comment style used in production code.
8. **Plain-English inline comments** explaining intent — same project rule as production code (verify in any test file: every block has explanatory comments).

### Builder Helpers for Entity Fixtures

Tests that need a `League` or `Player` build them via the Lombok builder — never via `new`. Each test class has a static helper:

```java
// MatchServiceTest
private static League leagueWithSport(Long id, String sportSlug, Integer externalId) {
    Sport sport = Sport.builder().slug(sportSlug).build();
    return League.builder().id(id).sport(sport).externalId(externalId).build();
}

// PlayerServiceCareerStatsTest
private static Player playerInSport(Long id, String sportSlug, String externalId,
                                    String playerName, Integer leagueExternalId) { ... }
```

**Why:** lets each test wire up exactly the lazy-loaded chain (`player → team → league → sport`) the routing logic walks, without booting Hibernate.

## RestClient Deep-Stub Pattern (NbaApiServiceTest)

`NbaApiService` calls Spring's `RestClient` via a fluent chain:
```java
restClient.get().uri(path).retrieve().body(Class)
```

Mocking each return type in that chain manually is tedious. Use Mockito's **`RETURNS_DEEP_STUBS`** answer mode to auto-mock the entire chain:

```java
@SuppressWarnings({"unchecked", "rawtypes"})  // RestClient generics
@ExtendWith(MockitoExtension.class)
class NbaApiServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    RestClient restClient;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    RestClient standingsClient;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    RestClient statsClient;

    NbaApiService nbaApiService;

    @BeforeEach
    void setUp() {
        nbaApiService = new NbaApiService(restClient, standingsClient, statsClient);
    }

    @Test
    void fetchGameDtosByDate_finalGame_mapsStatusToFinished() {
        when(restClient.get().uri(anyString()).retrieve()
                .body(NbaApiService.EspnScoreboardResponse.class))
                .thenReturn(scoreboardWith("STATUS_FINAL", "112", "98"));

        List<MatchDto> result = nbaApiService.fetchGameDtosByDate(LocalDate.of(2025, 4, 20), 7L);

        assertThat(result.get(0).status()).isEqualTo("FINISHED");
    }
}
```

**How it works:** with `RETURNS_DEEP_STUBS`, every intermediate call (`.get()`, `.uri(...)`, `.retrieve()`) returns a sub-mock automatically. The same sub-mock is returned regardless of argument matchers on the intermediate methods, so the stub is independent of the exact URL. You only stub the final `.body(Class)` call with the test fixture.

**When to use it:** any service that wraps `RestClient`. Apply the same approach to `NflApiService`, `ApiFootballService`, `BallDontLieService`, `ExternalApiService` when adding tests for them.

## Package-Private Test Constructors

`NbaApiService` (and `NflApiService` by the same pattern) cannot easily be tested via `@InjectMocks` because their primary constructor takes `@Value` URL strings — Mockito doesn't know what to pass.

**Solution:** the production class declares **two constructors**:

```java
// Production constructor — Spring uses this. Explicit @Autowired so Spring knows which one to pick.
@Autowired
public NbaApiService(
        @Value("${external-api.nba.base-url}") String baseUrl,
        @Value("${external-api.nba.standings-url}") String standingsUrl,
        @Value("${external-api.nba.stats-url}") String statsUrl) {
    this.restClient      = RestClient.builder().baseUrl(baseUrl).build();
    this.standingsClient = RestClient.builder().baseUrl(standingsUrl).build();
    this.statsClient     = RestClient.builder().baseUrl(statsUrl).build();
}

// Package-private test constructor — accepts pre-built RestClient instances.
// Same package = same com.onestopsports.service, so NbaApiServiceTest can call it.
NbaApiService(RestClient restClient, RestClient standingsClient, RestClient statsClient) {
    this.restClient      = restClient;
    this.standingsClient = standingsClient;
    this.statsClient     = statsClient;
}
```

**Key rule:** when adding multiple constructors, the production one MUST be marked `@Autowired` explicitly. Without it, Spring sees two ambiguous constructors and fails startup.

The test class lives in `package com.onestopsports.service` so it can call the package-private constructor without making it `public`.

## `@WebMvcTest` Slice Test Pattern (AuthControllerTest)

`@WebMvcTest` loads ONLY the web layer — controllers, filters, Spring Security. No services, no database, no DataLoader. Fast and focused.

**Mandatory setup for this codebase:**

```java
@WebMvcTest(
        value = AuthController.class,
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class
)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private AuthService authService;
    @MockBean private JwtUtil jwtUtil;

    @Test
    void register_validRequest_returns201AndToken() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(new AuthResponse("fake.jwt.token", "james"));

        mockMvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("fake.jwt.token"));
    }
}
```

**Three gotchas that took real debugging to find — copy this setup verbatim when adding new slice tests:**

### 1. `excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class`

Without it, Spring Boot auto-configures an in-memory `InMemoryUserDetailsManager` AND our mocked `AuthService` (which `implements UserDetailsService`). The container then sees two beans of the same type and crashes with `NoUniqueBeanDefinitionException`.

### 2. `@Import(SecurityConfig.class)`

`@WebMvcTest` only scans web-tier beans (controllers, filters, `@ControllerAdvice`). `@Configuration` classes like `SecurityConfig` are NOT picked up automatically. Without this import, Spring Security falls back to its default "deny all" rule and EVERY request returns 401 — even public endpoints like `/api/auth/register`.

### 3. `.with(csrf())` on every POST

CSRF protection is enabled by default in Spring Security 6 for slice tests (even though it's disabled in our production `SecurityConfig`). MockMvc must call `.with(csrf())` on POST/PUT/DELETE requests or the request is rejected with 403 before reaching the controller.

The import comes from `org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf` — requires the `spring-security-test` dependency.

## Context-Load Smoke Test (OneStopSportsApplicationTests)

```java
@SpringBootTest
@ActiveProfiles("test")
class OneStopSportsApplicationTests {

    @MockBean
    RedisConnectionFactory redisConnectionFactory;

    @Test
    void contextLoads() {
        // If this test passes, the entire Spring context wired up correctly.
    }
}
```

**Why `@MockBean RedisConnectionFactory` is required (the non-obvious bit):**

`RedisConfig` is a **user `@Configuration`** in our codebase — not part of Spring Boot's auto-configuration. Excluding `RedisAutoConfiguration` in `application-test.yml` only stops Spring Boot's auto-bean from being created; it does NOT prevent our `RedisConfig` from running. `RedisConfig` builds a `RedisCacheManager`, which needs a `RedisConnectionFactory` injected. Without a real Redis instance AND without a mock, the bean factory fails and the context test crashes.

The `@MockBean RedisConnectionFactory` provides a no-op factory so `RedisConfig` can construct its CacheManager bean without ever opening a network connection.

## Test Profile Configuration (`application-test.yml`)

Activated by `@ActiveProfiles("test")` on `OneStopSportsApplicationTests`.

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
    database-platform: org.hibernate.dialect.H2Dialect
  flyway:
    enabled: false                         # H2 doesn't need our Postgres migrations
  cache:
    type: none                             # No-op cache so @SpringBootTest doesn't need Redis
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
      - org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration
  data:
    redis:
      host: localhost
      port: 6379

jwt:
  secret: bXlzdXBlcnNlY3JldGtleWZvcmptYXRjaGRheWFwcGxpY2F0aW9u
  expiration-ms: 86400000
```

**Three things this file does:**
1. Replaces PostgreSQL with H2 in PostgreSQL-compatibility mode (no real DB needed).
2. Disables Flyway (H2 starts empty and Hibernate's `create-drop` recreates the schema).
3. Stops Redis from being required — `spring.cache.type: none` makes all `@Cacheable` calls passthrough, and the two `autoconfigure.exclude` lines stop Spring Boot's Redis auto-configs from booting.

**Note** — the autoconfigure excludes alone are NOT sufficient because our custom `RedisConfig @Configuration` runs regardless. That's why `OneStopSportsApplicationTests` also needs `@MockBean RedisConnectionFactory` (see above).

## Common Patterns

### Error / exception testing

```java
assertThatThrownBy(() -> authService.register(registerRequest))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Username already taken");
```

### Verifying a method was NOT called

```java
verify(userRepository, never()).save(any());
verify(externalApiService, never()).fetchMatchDtosByCompetition(anyInt(), any());
```

Critical for routing tests — proves a basketball league NEVER touches `ExternalApiService`.

### Argument-matcher trap with primitive `int`

`ExternalApiService.fetchMatchDtosByCompetition(int competitionId, LocalDate date)` takes a primitive `int`. Always use `anyInt()` — never `any()`:

```java
// CORRECT
verify(externalApiService, never()).fetchMatchDtosByCompetition(anyInt(), any());

// WRONG — Mockito returns null, which auto-unboxes to NullPointerException
verify(externalApiService, never()).fetchMatchDtosByCompetition(any(), any());
```

### Stubbing a method that returns `void`

```java
doThrow(new BadCredentialsException("Bad credentials"))
        .when(authenticationManager)
        .authenticate(any(UsernamePasswordAuthenticationToken.class));
```

### Suppressing unchecked-cast warnings on RestClient stubs

```java
@SuppressWarnings({"unchecked", "rawtypes"})  // RestClient generics
class NbaApiServiceTest { ... }
```

## What's NOT Tested Yet

The 57 tests cover the highest-risk routing logic, security flows, and the trickiest external-API service (`NbaApiService`). Known gaps:

- `NflApiService` — has no tests yet; mirror `NbaApiServiceTest` for consistency (use the same `RETURNS_DEEP_STUBS` pattern).
- `ExternalApiService` — football-data.org client has no dedicated test.
- `UserService`, `TeamService`, `PlayerService` (non-stats methods), `SportService`, `SearchService` — no tests yet.
- `ApiFootballService` and `BallDontLieService` — covered indirectly via `PlayerServiceCareerStatsTest` but no dedicated tests.
- Repository tests — none. The derived queries are simple enough to skip for now.
- Integration / E2E tests — none. There's no Testcontainers setup against real Postgres + Redis.
- Frontend — no test suite at all (no Vitest, no Jest, no Playwright).

---

*Testing analysis: 2026-05-21*
