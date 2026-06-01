<!-- refreshed: 2026-05-21 -->
# Architecture

**Analysis Date:** 2026-05-21

## System Overview

```text
┌───────────────────────────────────────────────────────────────────────────┐
│                        Browser — React 18 + TS SPA                        │
│  ┌────────────┐  ┌─────────────────┐  ┌─────────────────────────────────┐ │
│  │ Pages      │  │ React Query     │  │ useLiveScores (STOMP / WS)      │ │
│  │ Components │  │ axios client    │  │ AuthContext / ThemeContext      │ │
│  │ `frontend/src/pages` `frontend/src/api/client.ts` `frontend/src/hooks/useLiveScores.ts` │ │
│  └─────┬──────┘  └────────┬────────┘  └────────────────┬────────────────┘ │
└────────┼──────────────────┼────────────────────────────┼──────────────────┘
   HTTP  │           HTTP   │                  WebSocket │  (STOMP /ws)
         ▼                  ▼                            ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                Spring Boot 3.4.4 backend — `com.onestopsports`            │
│ ┌───────────────────────────────────────────────────────────────────────┐ │
│ │  Filter Chain                                                         │ │
│ │  JwtAuthFilter  →  SecurityFilterChain  →  Dispatcher                 │ │
│ │  `security/JwtAuthFilter.java`   `config/SecurityConfig.java`         │ │
│ └───────────────────────────────────────────────────────────────────────┘ │
│ ┌───────────────────────────────────────────────────────────────────────┐ │
│ │  Controllers (REST)                          GlobalExceptionHandler   │ │
│ │  Sport / League / Team / Player / Match /    `controller/Global...`   │ │
│ │  Auth / User / Search                                                 │ │
│ │  `controller/*Controller.java`                                        │ │
│ └─────┬─────────────────────────────────────────────────────────────────┘ │
│       ▼                                                                   │
│ ┌───────────────────────────────────────────────────────────────────────┐ │
│ │  Services (business logic + multi-sport routing)                      │ │
│ │  MatchService / LeagueService / PlayerService / TeamService /         │ │
│ │  SportService / UserService / AuthService / SearchController glue     │ │
│ │  ────────── External-API adapters ──────────                          │ │
│ │  ExternalApiService (football-data.org)                               │ │
│ │  NbaApiService (ESPN)   NflApiService (ESPN)                          │ │
│ │  BallDontLieService (NBA bios)    ApiFootballService (soccer stats)   │ │
│ │  `service/*.java`                                                     │ │
│ └─────┬───────────────────────────────────────────┬─────────────────────┘ │
│       ▼                                           ▼                       │
│ ┌────────────────────────────┐  ┌─────────────────────────────────────┐   │
│ │  Repositories (Spring Data)│  │  Redis cache + WebSocket broker     │   │
│ │  Sport/League/Team/Player/ │  │  CacheManager (matches, TTL 30s)    │   │
│ │  User/FavoriteTeam/        │  │  SimpMessagingTemplate              │   │
│ │  FavoritePlayer            │  │  `config/RedisConfig.java`          │   │
│ │  `repository/*.java`       │  │  `config/WebSocketConfig.java`      │   │
│ └─────┬──────────────────────┘  └────────────┬────────────────────────┘   │
└───────┼──────────────────────────────────────┼────────────────────────────┘
        ▼                                      ▼
┌────────────────────────────┐  ┌─────────────────────────────────────────┐
│  PostgreSQL `onestopsports`│  │  Redis 7 (in-memory cache + pub/sub)    │
│  Flyway: V1..V6 applied    │  │  Key `matches::SimpleKey []`, TTL 30s   │
│  `src/main/resources/db/   │  │                                         │
│   migration`               │  │                                         │
└────────────────────────────┘  └─────────────────────────────────────────┘
        ▲
        │  startup seeding (CommandLineRunner) + on-demand fetches
        │
┌────────────────────────────────────────────────────────────────────────┐
│  External APIs                                                         │
│  football-data.org v4  (Futbol matches / standings / squads)           │
│  ESPN unofficial API   (NBA + NFL teams / rosters / scoreboard / stats)│
│  balldontlie.io        (NBA player biographical enrichment)            │
│  v3.football.api-sports.io (per-season football player stats)          │
└────────────────────────────────────────────────────────────────────────┘
```

## Component Responsibilities

| Component | Responsibility | File |
|-----------|----------------|------|
| Application bootstrap | Boots Spring; enables caching + scheduling | `src/main/java/com/onestopsports/OneStopSportsApplication.java` |
| Security filter chain | Stateless JWT auth; CORS; route-level access rules | `src/main/java/com/onestopsports/config/SecurityConfig.java` |
| JWT filter | Per-request token extraction & `SecurityContext` population | `src/main/java/com/onestopsports/security/JwtAuthFilter.java` |
| Password encoder bean | Isolated `PasswordEncoder` — breaks `SecurityConfig` ↔ `AuthService` cycle | `src/main/java/com/onestopsports/config/PasswordConfig.java` |
| WebSocket broker | STOMP `/topic` broker on `/ws`; injects Spring Boot `ObjectMapper` | `src/main/java/com/onestopsports/config/WebSocketConfig.java` |
| Redis cache manager | `RedisCacheManager` with custom `ObjectMapper` (JavaTimeModule + `EVERYTHING` typing); 30s TTL | `src/main/java/com/onestopsports/config/RedisConfig.java` |
| Global error handler | `@RestControllerAdvice` mapping every thrown exception to a uniform `ErrorResponseDto` | `src/main/java/com/onestopsports/controller/GlobalExceptionHandler.java` |
| REST controllers | HTTP entry points; thin, delegate to services | `src/main/java/com/onestopsports/controller/*Controller.java` |
| Multi-sport router service | Routes by `league.getSport().getSlug()` to the correct external API service | `src/main/java/com/onestopsports/service/MatchService.java`, `LeagueService.java`, `PlayerService.java` |
| Football API adapter | `RestClient` wrapper around football-data.org (matches, standings, events, squads) | `src/main/java/com/onestopsports/service/ExternalApiService.java` |
| NBA API adapter | `RestClient` wrapper around ESPN (teams, rosters, scoreboard, standings, career stats) | `src/main/java/com/onestopsports/service/NbaApiService.java` |
| NFL API adapter | `RestClient` wrapper around ESPN (teams, rosters, scoreboard, standings, career stats) | `src/main/java/com/onestopsports/service/NflApiService.java` |
| NBA bio enrichment | `RestClient` wrapper around balldontlie.io for `PlayerBioDto` | `src/main/java/com/onestopsports/service/BallDontLieService.java` |
| Football stats adapter | `RestClient` wrapper around api-sports.io with lazy player-ID resolution | `src/main/java/com/onestopsports/service/ApiFootballService.java` |
| Live-score scheduler | `@Scheduled(fixedDelay=30_000)` — diff snapshot, push WS, refresh Redis | `src/main/java/com/onestopsports/service/MatchService.java` (`refreshLiveMatchCache`) |
| Data seeders | `CommandLineRunner` chains that populate sport → league → team → player on first boot | `src/main/java/com/onestopsports/config/DataLoader.java`, `NbaDataLoader.java`, `NflDataLoader.java` |
| Persistence | Spring Data JPA repositories over PostgreSQL | `src/main/java/com/onestopsports/repository/*Repository.java` |
| Schema migrations | Flyway-managed; ddl-auto is `validate` only | `src/main/resources/db/migration/V1..V6` |

## Pattern Overview

**Overall:** Classic layered Spring Boot architecture — `controller → service → repository → DB` — extended with a **strategy-by-sport-slug routing layer** inside the service tier and a **separate adapter service per external API** (one Spring bean per upstream provider).

**Key Characteristics:**
- DB schema is **sport-agnostic** (`sport → league → team → player`); per-sport quirks live only in the adapter services, never in entities or migrations
- DTOs are Java 21 **records**; entities are mutable JPA classes (Lombok). No MapStruct used in current code — DTO conversion is plain `toDto(...)` helpers inside each service
- Match scores and standings are **never persisted** — always fetched live and cached in Redis for 30 seconds
- Players, teams and leagues **are persisted** — seeded once at startup and looked up by DB id at request time
- Communication with the browser is **dual-channel**: REST for everything, plus a single STOMP topic (`/topic/matches/live`) for push updates

## Layers

**Configuration (`config/`):**
- Purpose: Holds all `@Configuration` and `CommandLineRunner` beans
- Location: `src/main/java/com/onestopsports/config`
- Contains: `SecurityConfig`, `PasswordConfig`, `RedisConfig`, `WebSocketConfig`, `OpenApiConfig`, `DataLoader`, `NbaDataLoader`, `NflDataLoader`
- Depends on: `security/`, `service/` (data loaders only)
- Used by: Spring container at startup

**Security (`security/`):**
- Purpose: JWT issuance and validation
- Location: `src/main/java/com/onestopsports/security`
- Contains: `JwtUtil` (sign/verify; jjwt 0.12.x API), `JwtAuthFilter` (`OncePerRequestFilter` populating `SecurityContextHolder`)
- Depends on: `service.AuthService` (implements `UserDetailsService`)

**Controllers (`controller/`):**
- Purpose: HTTP entry points; one controller per resource family
- Location: `src/main/java/com/onestopsports/controller`
- Contains: `SportController`, `LeagueController`, `TeamController`, `PlayerController`, `MatchController`, `AuthController`, `UserController`, `SearchController`, `GlobalExceptionHandler`
- Depends on: services + DTOs
- Pattern: Constructor injection; controllers are thin pass-throughs returning `ResponseEntity<…>`

**Services (`service/`):**
- Purpose: Business logic + external API adapters
- Location: `src/main/java/com/onestopsports/service`
- Contains:
  - Business services: `SportService`, `LeagueService`, `TeamService`, `PlayerService`, `MatchService`, `AuthService`, `UserService`
  - External adapters: `ExternalApiService` (football-data.org), `NbaApiService` (ESPN), `NflApiService` (ESPN), `BallDontLieService` (balldontlie), `ApiFootballService` (api-sports.io)
- Depends on: repositories, `CacheManager`, `SimpMessagingTemplate`, `RestClient`

**Repositories (`repository/`):**
- Purpose: Spring Data JPA interfaces — derived queries only, no custom JPQL
- Location: `src/main/java/com/onestopsports/repository`
- Contains: `SportRepository`, `LeagueRepository`, `TeamRepository`, `PlayerRepository`, `UserRepository`, `FavoriteTeamRepository`, `FavoritePlayerRepository`
- Notable derived queries: `LeagueRepository.findBySport_Slug(String)` — joins through the `sport` relationship without a manual JPQL

**Domain model (`model/`):**
- Purpose: JPA entities
- Location: `src/main/java/com/onestopsports/model`
- Contains: `Sport`, `League`, `Team`, `Player`, `UserAccount`, `FavoriteTeam`, `FavoritePlayer`
- Pattern: `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`. `@Data` is **deliberately avoided** to prevent `toString`/`hashCode` recursion on bidirectional relationships. All `@ManyToOne` are `FetchType.LAZY`

**DTOs (`dto/`):**
- Purpose: Wire-format payloads; all Java 21 records
- Location: `src/main/java/com/onestopsports/dto`
- Notable: `PlayerCareerStatsDto` is a sport-agnostic "table-shaped" record (`categories[].labels[]` + `seasons[].values[]`) so the same payload works for NBA, NFL and football; `MatchDto` includes a `timezone` field (`"ET"` for NBA/NFL, `null` for football)
- Inbound requests carry Jakarta `@NotBlank`/`@Email`-style validation on record components

## Data Flow

### Primary Request Path — REST (e.g. `GET /api/matches?league=1&date=...`)

1. Browser fires HTTP request via `frontend/src/api/client.ts` (axios) — interceptor attaches `Authorization: Bearer <jwt>` if a token is present in `localStorage`
2. `JwtAuthFilter.doFilterInternal` (`src/main/java/com/onestopsports/security/JwtAuthFilter.java:31`) parses the bearer token, validates it via `JwtUtil`, loads `UserDetails` via `AuthService.loadUserByUsername`, populates `SecurityContextHolder`
3. `SecurityConfig.filterChain` (`src/main/java/com/onestopsports/config/SecurityConfig.java:34`) checks the route — `GET /api/**` is `permitAll`, `/api/users/me/**` is `authenticated`
4. `MatchController.getMatches` (`src/main/java/com/onestopsports/controller/MatchController.java:33`) delegates to `MatchService.getMatchesByLeagueAndDate`
5. `MatchService.getMatchesByLeagueAndDate` (`src/main/java/com/onestopsports/service/MatchService.java:97`) is `@Transactional(readOnly=true)`; looks up the `League`, resolves `league.getSport().getSlug()`, then routes:
   - `"basketball"` → `NbaApiService.fetchGameDtosByDate(date, leagueId)`
   - `"american-football"` → `NflApiService.fetchGameDtosByDate(date, leagueId)`
   - default (`"football"`) → `ExternalApiService.fetchMatchDtosByCompetition(externalId, date)`
6. The chosen adapter calls the upstream API via `RestClient`, maps the JSON to `MatchDto` records, returns them
7. `MatchController` wraps the list in `ResponseEntity.ok(...)`; Spring's `MappingJackson2HttpMessageConverter` (using the Boot-configured `ObjectMapper`) serialises to JSON

### Live-Score Push Path — WebSocket (`/topic/matches/live`)

1. `MatchService.refreshLiveMatchCache` (`src/main/java/com/onestopsports/service/MatchService.java:159`) fires every 30s on the `@Scheduled` thread
2. Fetches football live matches (`externalApiService.fetchLiveMatchDtos`) and NBA + NFL live matches for today (`fetchNonFootballLiveMatches`, which iterates `leagueRepository.findBySport_Slug("basketball")` and `findBySport_Slug("american-football")`)
3. Builds a snapshot `Map<Long,String>` of `"home:away:status"` and compares with `previousSnapshot` (a `volatile ConcurrentHashMap`)
4. **Only on change** — writes the combined list into Redis (`cache.put(SimpleKey.EMPTY, current)` — same key the no-arg `@Cacheable("matches") getLiveMatches()` would generate) AND broadcasts via `messagingTemplate.convertAndSend("/topic/matches/live", current)`
5. STOMP message converter (`WebSocketConfig.configureMessageConverters` — `src/main/java/com/onestopsports/config/WebSocketConfig.java:57`) serialises with the auto-configured `ObjectMapper` (has `JavaTimeModule`, so `LocalDateTime` becomes ISO-8601)
6. Browser-side `useLiveScores` (`frontend/src/hooks/useLiveScores.ts`) receives the message, parses JSON, calls `onUpdate(matches)` which `LivePage` uses to call `queryClient.setQueryData(["matches","live"], matches)` — React re-renders instantly
7. REST `GET /api/matches/live` remains a fallback at 60s polling; on cache hit Spring serves from Redis without round-tripping any upstream API

### Authentication Path — Register / Login

1. `AuthController` (`src/main/java/com/onestopsports/controller/AuthController.java`) receives `POST /api/auth/register` or `/login` (both `permitAll`)
2. `AuthService.register` checks for duplicate username/email (throws `ResponseStatusException(CONFLICT)` on hit), hashes the password with `PasswordEncoder` (BCrypt — declared in `PasswordConfig`), saves the `UserAccount`, returns `AuthResponse(token, username)`
3. `AuthService.login` calls `authenticationManager.authenticate(...)` (the bean is `@Lazy`-injected to break the circular dependency); on success returns a freshly minted JWT via `JwtUtil.generateToken`
4. On the client, `AuthContext` stores the token in `localStorage`; the axios interceptor attaches it to every subsequent request

### Career-Stats Path — multi-sport routing in `PlayerService.getPlayerCareerStats`

1. `PlayerController` → `PlayerService.getPlayerCareerStats(id)` (`src/main/java/com/onestopsports/service/PlayerService.java:91`) — `@Transactional` because the lazy chain `player → team → league → sport` must resolve and (for football) a new `externalId` may be persisted
2. Switch on `player.getTeam().getLeague().getSport().getSlug()`:
   - `"basketball"` → `NbaApiService.fetchCareerStats(externalId)` (ESPN athlete ID)
   - `"american-football"` → `NflApiService.fetchCareerStats(externalId)` (ESPN athlete ID)
   - `"football"` → `fetchFootballStats(player)` which lazily resolves the API-Football player ID, persists it on the row, then fetches per-season stats
3. `Optional.empty()` → controller responds `204 No Content`

**State Management:**
- DB writes: `AuthService.register/login`, `UserService` favourites mutations, `PlayerService.fetchFootballStats` (lazy externalId backfill), data loaders
- In-memory: `MatchService.previousSnapshot` (`volatile ConcurrentHashMap`) for live-diff comparison
- Redis: single cache name `matches`, key `SimpleKey.EMPTY`, value = combined live match list
- Browser: React Query cache (in-memory), `localStorage` for JWT + theme

## Key Abstractions

**Sport-slug strategy routing:**
- Purpose: Pick the right external API adapter at runtime
- Examples: `MatchService.getMatchesByLeagueAndDate` (`service/MatchService.java:97`), `LeagueService.getStandings` (`service/LeagueService.java:63`), `PlayerService.getPlayerCareerStats` (`service/PlayerService.java:91`)
- Pattern: `switch (league.getSport().getSlug())` over `"basketball"` / `"american-football"` / default (`"football"`)

**External API adapter:**
- Purpose: Encapsulate one upstream HTTP provider per Spring bean
- Examples: `ExternalApiService`, `NbaApiService`, `NflApiService`, `BallDontLieService`, `ApiFootballService`
- Pattern: A `RestClient` field built once in the constructor from `@Value("${external-api.<provider>.…}")` properties, with private inner `record` types mirroring the upstream JSON; mapper methods convert those records to project DTOs

**Sport-agnostic career stats DTO:**
- Purpose: One wire format that fits NBA / NFL / football stats without modelling each sport's stat schema
- File: `dto/PlayerCareerStatsDto.java`
- Pattern: `categories[]` of `(labels[], seasons[], career)` rows — the UI iterates the grid blindly

**JWT-stateless filter:**
- Purpose: Authenticate every request without server-side session state
- File: `security/JwtAuthFilter.java`
- Pattern: `OncePerRequestFilter` registered `addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)` in `SecurityConfig`

## Entry Points

**HTTP / REST:**
- Location: `src/main/java/com/onestopsports/controller/*Controller.java`
- Base path: `/api/**`
- Triggers: Frontend axios calls via `frontend/src/api/*.ts` modules

**WebSocket / STOMP:**
- Location: `config/WebSocketConfig.java`
- Endpoint: `ws://<host>/ws` (plain WebSocket, no SockJS)
- Topic: `/topic/matches/live` (server → client only)
- Triggers: Browser-side `useLiveScores` hook connecting on `LivePage` mount

**Scheduled jobs:**
- Location: `service/MatchService.java:159` — `refreshLiveMatchCache()` `@Scheduled(fixedDelay=30_000)`
- Triggers: Spring scheduler (enabled by `@EnableScheduling` on `OneStopSportsApplication`)

**CommandLineRunner seeding (startup):**
- Locations: `config/DataLoader.java` (Futbol), `config/NbaDataLoader.java` (NBA), `config/NflDataLoader.java` (NFL)
- Triggers: Spring lifecycle — runs once per boot; idempotent (skip checks based on existing row counts and presence of `crestUrl`/`externalId`)

## Architectural Constraints

- **Threading:** Tomcat thread per HTTP request (default Spring Boot servlet stack). The live-score scheduler runs on a separate Spring scheduler thread — `MatchService.previousSnapshot` is `volatile ConcurrentHashMap` to make cross-thread visibility safe.
- **Open Session In View (OSIV):** Enabled by default in Spring Boot. Several mapper paths rely on it — notably `PlayerService.resolvePhotoUrl` walks `player.team.league.sport.slug` (three lazy hops). Only the **stateful** flows that need explicit transaction scope are marked `@Transactional` (`MatchService.getMatchesByLeagueAndDate`, `LeagueService.getStandings`'s `league.getSport()` reads via OSIV, `PlayerService.getPlayerCareerStats`).
- **Global state:** `MatchService.previousSnapshot` is the only module-level mutable state outside Spring beans. `AuthContext`/`ThemeContext` + `localStorage` are the equivalents on the client.
- **Circular dependency (resolved):** Original cycle was `JwtAuthFilter → AuthService → PasswordEncoder (in SecurityConfig) → JwtAuthFilter`. Broken by (1) moving `PasswordEncoder` into its own `PasswordConfig`, and (2) `@Lazy AuthenticationManager` constructor injection in `AuthService`.
- **DDL:** `spring.jpa.hibernate.ddl-auto: validate` — Flyway is the only thing allowed to change the schema. Entity changes without a matching migration will fail validation at boot.
- **`UserAccount` not `User`:** `user` is a PostgreSQL reserved word, so the entity is named `UserAccount` and the table is `user_account`.

## Anti-Patterns

### Treating live data like persistent data

**What happens:** Tempting to add a `match` table because everywhere else is sport → league → team → player.
**Why it's wrong:** Match scores change minute-by-minute. Persisting them would (a) double the upstream calls (write + read instead of cache only), (b) make Redis irrelevant, (c) create a permanent reconciliation burden.
**Do this instead:** Keep match data ephemeral. Cache in Redis with a 30s TTL (`RedisConfig.cacheManager`) and let `MatchService.refreshLiveMatchCache` keep it warm.

### Catching `ResponseStatusException` with the generic `Exception` handler

**What happens:** Adding a new `@ExceptionHandler(Exception.class)` above or without `@ExceptionHandler(ResponseStatusException.class)` causes 404s to become 500s, because Spring matches the most generic handler first if it appears first.
**Why it's wrong:** Loses the HTTP status that services intentionally attached via `new ResponseStatusException(HttpStatus.NOT_FOUND, …)`.
**Do this instead:** Always keep the specific handlers in `GlobalExceptionHandler.java` BEFORE the `Exception.class` catch-all (current code is correct — see `controller/GlobalExceptionHandler.java:82` then `:108`).

### Using `@Data` on JPA entities

**What happens:** Lombok-generated `equals`/`hashCode`/`toString` traverse `@ManyToOne` back-references and recurse forever.
**Why it's wrong:** Stack overflows when logging entities or putting them in sets/maps.
**Do this instead:** Use the explicit `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor` quartet — see every entity in `model/`.

### Bare `GenericJackson2JsonRedisSerializer()` / default STOMP converter

**What happens:** The no-arg `GenericJackson2JsonRedisSerializer` and the default STOMP `MappingJackson2MessageConverter` both create a fresh `ObjectMapper` without `JavaTimeModule`. Anything containing a `LocalDateTime` (e.g. `MatchDto.startTime`) throws on serialise.
**Why it's wrong:** Manifests as opaque 500s from `/api/matches/live` and silent WebSocket failures.
**Do this instead:** Use the custom mappers wired in `RedisConfig.cacheManager` and `WebSocketConfig.configureMessageConverters` — both inject `JavaTimeModule`. If you add new caches or converters, reuse the same `ObjectMapper`.

### Adding a new external API by editing existing adapters

**What happens:** Mixing two providers' record types into one service class.
**Why it's wrong:** Each provider has its own quirks (e.g. NBA roster is a flat `athletes[]`, NFL is grouped `positionGroups[].items[]`; ESPN standings live on a different subdomain). Co-locating them turns the file into a swamp.
**Do this instead:** New provider → new `@Service` adapter (one `RestClient`, inner records, mapper methods). Surface it via a new `switch` arm in the sport-slug router in `MatchService` / `LeagueService` / `PlayerService`.

## Error Handling

**Strategy:** Service layer throws `ResponseStatusException` (with explicit `HttpStatus` + reason) or lets Spring/Jakarta validation exceptions propagate. `GlobalExceptionHandler` is the single point of HTTP-status translation.

**Patterns:**
- `MethodArgumentNotValidException` → 400 (concatenated field errors)
- `HttpMessageNotReadableException` → 400 ("Malformed or missing request body")
- `BadCredentialsException` → 401 (deliberately vague — doesn't reveal whether the username exists)
- `AccessDeniedException` → 403
- `ResponseStatusException` → status it carries (must come BEFORE the catch-all — see anti-pattern above)
- `DataIntegrityViolationException` → 409 (used for duplicate username/email)
- `Exception` → 500 (full stack trace logged server-side; vague message returned to client)
- All responses share the `ErrorResponseDto(status, error, message, timestamp)` shape (`dto/ErrorResponseDto.java`)

## Cross-Cutting Concerns

**Logging:** SLF4J via Lombok-free `LoggerFactory.getLogger(MatchService.class)` calls inside services and `GlobalExceptionHandler`. Spring Boot's default logback config — no custom appenders.

**Validation:** Jakarta Validation annotations on DTO record components (e.g. `RegisterRequest`, `AuthRequest`). Triggered by `@Valid` in controller signatures. Failures are mapped to 400 by the global handler.

**Authentication & Authorisation:**
- Authentication: JWT bearer token via `JwtAuthFilter`; tokens minted by `JwtUtil.generateToken` after `AuthenticationManager.authenticate` succeeds
- Authorisation: Coarse rules in `SecurityConfig.filterChain` — `GET /api/**` and `/api/auth/**` and `/ws/**` and `/swagger-ui/**` are `permitAll`; `/api/users/me/**` and any mutating endpoint require `authenticated()`
- Every user has the `USER` role only (no admin tier currently)

**Caching:**
- `@Cacheable("matches")` on `MatchService.getLiveMatches()` — single entry keyed by `SimpleKey.EMPTY`
- `@EnableCaching` on `OneStopSportsApplication`
- 30s TTL set programmatically in `RedisConfig.cacheManager` (overrides `application.yml`)
- Manual cache writes by `MatchService.refreshLiveMatchCache` use `cacheManager.getCache("matches").put(SimpleKey.EMPTY, current)`

**Scheduled jobs:**
- `@EnableScheduling` on `OneStopSportsApplication`
- One job: `MatchService.refreshLiveMatchCache` (`@Scheduled(fixedDelay=30_000)`)

**Real-time push:**
- `@EnableWebSocketMessageBroker` in `WebSocketConfig`
- Simple broker on prefix `/topic`; app destination prefix `/app`
- One topic in use: `/topic/matches/live`

**Configuration profiles:**
- `application.yml` — default (localhost)
- `application-local.yml` — gitignored; holds real API keys + JWT secret
- `application-docker.yml` — for full-stack Docker Compose; reads env vars
- `application-test.yml` — disables Redis (`spring.cache.type: none`) and excludes Redis auto-configurations

**API documentation:**
- springdoc-openapi auto-generated from `@RestController` classes
- Swagger UI at `/swagger-ui/index.html`; OpenAPI JSON at `/v3/api-docs`
- `OpenApiConfig` adds the JWT bearer scheme so locked endpoints can be tested from the UI
- `SecurityConfig` permits `/swagger-ui/**` and `/v3/api-docs/**`

---

*Architecture analysis: 2026-05-21*
