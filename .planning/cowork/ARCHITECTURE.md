# OneStopSports — Architecture

> **What this doc is:** How OneStopSports is organised internally — the layered structure, the multi-sport routing pattern, how requests flow, how live scores get pushed, how the cache + WebSocket work together, and the few load-bearing constraints you must respect. Read this once you're oriented on what the app does and its high-level shape.

---

## System diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                Browser — React 18 + TS SPA                      │
│  Pages · Components · React Query · useLiveScores (STOMP/WS)    │
│  AuthContext · ThemeContext                                     │
└────────┬───────────────────┬────────────────────┬───────────────┘
   HTTP  │           HTTP    │       WebSocket    │  (STOMP /ws)
         ▼                   ▼                    ▼
┌─────────────────────────────────────────────────────────────────┐
│   Spring Boot 3.4.4 — com.onestopsports                         │
│                                                                 │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │ JwtAuthFilter → SecurityFilterChain → DispatcherServlet │   │
│   └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│   Controllers (thin) → GlobalExceptionHandler                   │
│         │                                                       │
│         ▼                                                       │
│   Services (business + multi-sport routing)                     │
│         │                                                       │
│         │      ┌─────────── External-API adapters ──────────┐   │
│         │      │ ExternalApiService (football-data.org)     │   │
│         │      │ NbaApiService / NflApiService (ESPN)       │   │
│         │      │ BallDontLieService (NBA bios)              │   │
│         │      │ ApiFootballService (soccer stats)          │   │
│         │      └────────────────────────────────────────────┘   │
│         ▼                                                       │
│   Repositories (Spring Data JPA)    Redis + STOMP broker        │
└────────┬─────────────────────────────────┬──────────────────────┘
         ▼                                 ▼
┌────────────────────────┐  ┌─────────────────────────────────┐
│ PostgreSQL "onestopsports" │ Redis 7 (matches cache + push) │
│ Flyway V1..V9               │ Key matches::SimpleKey[] TTL30s│
└────────────────────────────┘                                │
        ▲                                                     │
        │ startup seeders + on-demand fetches                 │
┌─────────────────────────────────────────────────────────────┐
│ External APIs                                               │
│ football-data.org · ESPN NBA · ESPN NFL                     │
│ balldontlie.io · v3.football.api-sports.io                  │
└─────────────────────────────────────────────────────────────┘
```

## Pattern

**Classic layered Spring Boot** — `controller → service → repository → DB` — extended with **two architectural choices that shape every multi-sport feature**:

1. **A strategy-by-sport-slug routing layer inside the service tier.** Whenever a request could go to a different upstream depending on the sport, the service `switch`es on a sport slug. Same shape in three places:
   - `MatchService.getMatchesByLeagueAndDate` / `LeagueService.getStandings` — via `league.getSport().getSlug()`
   - `PlayerService.getPlayerCareerStats` / `PlayerService.resolvePhotoUrl` / `TeamService.getRosterForSeason` — via `team.getSport().getSlug()` (a direct `Team.sport` link added in the V9 team↔league many-to-many refactor, since a team can now belong to several leagues, so a league hop would be ambiguous)

2. **One Spring bean per external API.** Each upstream gets its own `@Service` with a `RestClient` instance, its own inner records mirroring the upstream JSON, and its own mapper methods. Never mix providers in one class.

The DB schema is **sport-agnostic** (`sport → league → team → player`). Per-sport quirks (ESPN's nested standings, football-data's competition IDs, API-Football's lazy ID lookup) live only inside the adapter services. The migrations don't know about NBA or NFL.

## Layer responsibilities

| Layer | Package | What it owns |
|---|---|---|
| Application | `(root)` | `OneStopSportsApplication.java` — `@SpringBootApplication @EnableCaching @EnableScheduling` |
| Configuration | `config/` | Security, Password, Redis, WebSocket, OpenAPI, and the three `CommandLineRunner` data loaders |
| Controllers | `controller/` | One `@RestController` per resource family; thin pass-throughs returning `ResponseEntity<…>`; plus `GlobalExceptionHandler` (`@RestControllerAdvice`) |
| Services | `service/` | Business logic + multi-sport routing + external-API adapters |
| Repositories | `repository/` | Spring Data JPA interfaces — **derived queries only**, no `@Query` annotations anywhere |
| Domain | `model/` | JPA entities (mutable Lombok classes) |
| DTOs | `dto/` | Java 21 records (request + response) |
| Security | `security/` | `JwtUtil` + `JwtAuthFilter` (`OncePerRequestFilter`) |

## Multi-sport routing — the canonical pattern

Whenever you add a feature that has a different upstream per sport, follow this shape:

```java
@Transactional(readOnly = true)
public List<MatchDto> getMatchesByLeagueAndDate(Long leagueId, LocalDate date) {
    if (leagueId == null || date == null) return Collections.emptyList();

    return leagueRepository.findById(leagueId).map(league -> {
        String sportSlug = league.getSport().getSlug();   // OSIV makes this lazy load work
        return switch (sportSlug) {
            case "basketball"        -> nbaApiService.fetchGameDtosByDate(date, league.getId());
            case "american-football" -> nflApiService.fetchGameDtosByDate(date, league.getId());
            default                  -> league.getExternalId() != null
                ? externalApiService.fetchMatchDtosByCompetition(league.getExternalId(), date)
                : Collections.<MatchDto>emptyList();
        };
    }).orElse(Collections.emptyList());
}
```

**Canonical sport slugs (load-bearing):** `"football"`, `"basketball"`, `"american-football"`. Switch literals must match exactly. Adding a new sport = add a new arm here AND in the other two router methods.

## Request flow — REST (e.g. `GET /api/matches?league=1&date=2026-05-21`)

1. Browser fires HTTP via `frontend/src/api/client.ts` (axios); request interceptor attaches `Authorization: Bearer <jwt>` from `localStorage` if a token exists.
2. `JwtAuthFilter.doFilterInternal` parses the token, validates via `JwtUtil`, loads `UserDetails` via `AuthService.loadUserByUsername`, populates `SecurityContextHolder`. The chain is fully stateless — no server session is created.
3. `SecurityConfig.filterChain` checks route rules — `/api/users/me/**` requires `authenticated()` and is declared **before** the broad `GET /api/**` `permitAll` (order is load-bearing: first match wins, so the auth rule must come first or protected GETs get bypassed). Unauthenticated hits on protected routes return a 401 JSON via the configured `AuthenticationEntryPoint`.
4. `MatchController.getMatches` delegates to `MatchService.getMatchesByLeagueAndDate`.
5. Service resolves the league, walks `league.getSport().getSlug()`, routes via the `switch` above to the matching adapter.
6. The adapter calls the upstream API via `RestClient`, maps JSON to `MatchDto` records, returns them.
7. Controller wraps in `ResponseEntity.ok(...)`; Spring's `MappingJackson2HttpMessageConverter` serialises using Boot's auto-configured `ObjectMapper`.

## Live-score push — WebSocket flow

The home/live page never polls. The path:

1. **Scheduler** — `MatchService.refreshLiveMatchCache` fires every 30s (`@Scheduled(fixedDelay = 30_000)`).
2. **Fetch** — calls `ExternalApiService.fetchLiveMatchDtos()` for football live matches, then iterates `leagueRepository.findBySport_Slug("basketball")` and `findBySport_Slug("american-football")` to pull NBA + NFL live games for today.
3. **Diff** — builds a snapshot `Map<Long,String>` of `"home:away:status"` per match and compares against `previousSnapshot` (a `volatile ConcurrentHashMap`).
4. **Push (only on change)** — writes the combined list to Redis (`cacheManager.getCache("matches").put(SimpleKey.EMPTY, current)`) AND broadcasts via `messagingTemplate.convertAndSend("/topic/matches/live", current)`.
5. **STOMP converter** (`WebSocketConfig.configureMessageConverters`) serialises with Boot's auto-configured `ObjectMapper` — which has `JavaTimeModule`, so `LocalDateTime` round-trips as ISO-8601.
6. **Browser** — `useLiveScores` (in `frontend/src/hooks/`) receives the message, calls `queryClient.setQueryData(['matches','live'], matches)`. React Query re-renders instantly with no extra refetch.
7. **REST fallback** — `GET /api/matches/live` polls every 60s as a safety net; serves from Redis cache (`@Cacheable("matches")`, key `SimpleKey.EMPTY`).

**Why diff-then-push?** Avoids flooding clients during quiet ticks (e.g. half-time, between games). Only events that change something hit the wire.

## Authentication flow

- **Register** — `AuthController` → `AuthService.register`; checks duplicates (throws `HttpStatus.CONFLICT` via `ResponseStatusException` on hit), BCrypts the password (`PasswordEncoder` bean lives in `PasswordConfig`, not `SecurityConfig` — see "Circular dependency resolution" below), saves the `UserAccount`, returns `AuthResponse(token, username)`.
- **Login** — `AuthService.login` calls `authenticationManager.authenticate(...)`. The `AuthenticationManager` is `@Lazy`-injected in `AuthService`'s constructor (manual constructor, not `@RequiredArgsConstructor`) to defer resolution and break a startup cycle.
- **Token** — minted by `JwtUtil.generateToken` using HMAC + Base64-decoded secret from `jwt.secret`. Default lifetime 24h (`jwt.expiration-ms: 86400000`). jjwt 0.12.x API: `Jwts.parser().verifyWith(key).parseSignedClaims(token)`.
- **Filter** — `JwtAuthFilter` (an `OncePerRequestFilter`) extracts the Bearer token from `Authorization`, validates, populates `SecurityContextHolder`.
- **Client** — `AuthContext` stores token + username in `localStorage`. The axios interceptor reads `localStorage` directly (NOT React context) so it works during the initial request before any component renders.

## Persistence + lazy loading

- All `@ManyToOne` relationships are `fetch = FetchType.LAZY`. Walking them (`player.team.league.sport`) triggers SQL on access.
- **OSIV (Open Session In View) is enabled by default in Spring Boot.** This keeps the Hibernate session alive for the full HTTP request, which is why mappers like `PlayerService.toDto` can walk `player.team.league.sport.slug` without an explicit `@Transactional` annotation.
- **Methods that need explicit transaction scope** (writes, or walks outside a web context) are marked `@Transactional` or `@Transactional(readOnly = true)`. Examples: `MatchService.getMatchesByLeagueAndDate`, `LeagueService.getStandings`, `PlayerService.getPlayerCareerStats`.
- **`spring.jpa.hibernate.ddl-auto: validate`** — schema is Flyway-owned. Entity changes without a matching migration fail at boot. Never edit a migration that has already been applied; always add the next version.

## Caching

- One cache name: `matches`. One entry, keyed `SimpleKey.EMPTY` (the cache key for the no-arg `getLiveMatches()` method).
- 30-second TTL, set **programmatically** in `RedisConfig` (overrides any YAML default when a custom `RedisCacheManager` bean is in play).
- `RedisConfig` builds a custom `ObjectMapper` with `JavaTimeModule` registered + `DefaultTyping.EVERYTHING`. **Critical**: the no-arg `GenericJackson2JsonRedisSerializer` constructor creates a bare `ObjectMapper` that cannot serialise `LocalDateTime`, which silently 500s any cached match with a `startTime`. Same root cause for STOMP — `WebSocketConfig.configureMessageConverters` overrides the default to inject Boot's mapper.
- `MatchService.refreshLiveMatchCache` writes manually via `cacheManager.getCache("matches").put(SimpleKey.EMPTY, current)` so the WebSocket push and the REST fallback see the same data.

## Error handling

`GlobalExceptionHandler` (`@RestControllerAdvice`) is the single source of HTTP-status truth. Every exception type maps to a uniform `ErrorResponseDto(status, error, message, timestamp)`:

| Exception | HTTP | Notes |
|---|---|---|
| `MethodArgumentNotValidException` | 400 | Field errors concatenated |
| `HttpMessageNotReadableException` | 400 | "Malformed or missing request body" |
| `ResponseStatusException` | passthrough | **Must come BEFORE the catch-all** |
| `BadCredentialsException` | 401 | Deliberately vague — doesn't reveal whether the username exists |
| `AccessDeniedException` | 403 | |
| `DataIntegrityViolationException` | 409 | Used for duplicate username/email |
| `Exception` | 500 | Full stack logged server-side; vague message to client |

**The ordering rule is real and easy to break.** If `@ExceptionHandler(Exception.class)` is declared before `@ExceptionHandler(ResponseStatusException.class)`, Spring matches the most generic handler first and your 404s come back as 500s.

## Circular dependency resolution

The original wiring had a cycle: `JwtAuthFilter → AuthService → PasswordEncoder (in SecurityConfig) → JwtAuthFilter`. Two fixes were applied:

1. **`PasswordConfig`** was extracted from `SecurityConfig` so `PasswordEncoder` lives in its own `@Configuration` class with no other dependencies.
2. **`AuthenticationManager`** is injected with `@Lazy` in `AuthService`'s manual constructor. Spring defers resolution until first use (login), breaking the startup cycle.

`AuthService` therefore does NOT use `@RequiredArgsConstructor` — it has a hand-written constructor so `@Lazy` can be applied to a parameter.

## Anti-patterns to avoid

These are real footguns that have caused real bugs in this codebase:

| Don't | Because | Do |
|---|---|---|
| Use `@Data` on JPA entities | Recurses through bidirectional relationships → `StackOverflowError` | Use `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor` quartet |
| Declare `@ExceptionHandler(Exception.class)` first in `GlobalExceptionHandler` | Catch-all intercepts `ResponseStatusException` → 404s become 500s | Always declare specific handlers BEFORE the catch-all |
| Use `new GenericJackson2JsonRedisSerializer()` (no-arg) | Bare `ObjectMapper`, no `JavaTimeModule` → 500s on `LocalDateTime` | Reuse the `ObjectMapper` from `RedisConfig` |
| Mix two providers in one `@Service` | Differing record shapes; differing quirks (e.g. NBA flat vs. NFL grouped rosters) | New provider → new `@Service` + new switch arm in the router |
| Persist live match data | Scores change minute-by-minute; would double upstream calls and make Redis pointless | Keep matches ephemeral — Redis 30s TTL + scheduler refresh |
| Add a parameter to `getLiveMatches()` | Cache key changes from `SimpleKey.EMPTY` → WebSocket push uses wrong key | Either keep it no-arg, or update the `cacheManager.put(...)` key together |

## Constraints worth knowing

- **Threading** — Tomcat thread per HTTP request. The live-score scheduler runs on a separate Spring scheduler thread; `MatchService.previousSnapshot` is `volatile ConcurrentHashMap` for cross-thread visibility.
- **`UserAccount`, not `User`** — `user` is a PostgreSQL reserved word.
- **Spring profiles** — `default` (localhost), `local` (real secrets in gitignored `application-local.yml`), `docker` (env-driven via `application-docker.yml`), `test` (Redis disabled).
- **ESPN time zone** — both `NbaApiService` and `NflApiService` convert UTC → ET in code with `OffsetDateTime.parse(...).atZoneSameInstant(ZoneId.of("America/New_York")).toLocalDateTime()`. `MatchDto.timezone == "ET"` flags this for the frontend. Football times stay UTC.

## Where to add new things

- **New REST endpoint on an existing resource** → method in the matching `<Resource>Controller.java` + method on the matching `<Resource>Service.java`. Update `SecurityConfig.filterChain` only if it's not a `GET /api/**` (which is already permitted).
- **New business resource** → new entity + Flyway migration (`V<next>__create_<resource>.sql`) + repository + DTO record + service + controller + matching TS interface in `frontend/src/types/index.ts` + axios module + page.
- **New external API provider** → new `@Service` adapter with its own `RestClient` and inner records, new config under `external-api.<provider>` in `application.yml` (and metadata entry in `META-INF/additional-spring-configuration-metadata.json`), new switch arm in the multi-sport routers if it corresponds to a new sport.
- **New React page** → component in `pages/`, route in `App.tsx`, nav item in **both** `Sidebar.tsx` and `BottomNav.tsx`, fetch helper in `api/`.
