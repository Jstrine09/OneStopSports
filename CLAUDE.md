# OneStopSports — Claude Code Context

## Project Overview
**OneStopSports** is a full-stack sports app inspired by Fotmob. It surfaces live scores, league standings, match timelines, lineups, and player/team stats. Users can create accounts and save favourite teams and players.

**Repo:** `/Users/james/Projects/OneStopSports`
**Related project (for reference):** `/Users/james/Projects/OnesToManys` — a simpler Spring Boot one-to-many demo that uses the same football-data.org API. Many patterns here were ported from it.

---

## Stack
| Layer | Technology |
|---|---|
| Backend | Java 21 + Spring Boot 3.4.4 |
| Database | PostgreSQL (`onestopsports` DB) |
| Migrations | Flyway (5 migrations — all applied) |
| Cache | Redis (30s TTL on live matches) |
| Auth | Spring Security 6 + JWT (jjwt 0.12.x) |
| Real-time | Spring WebSocket (STOMP) — fully wired; server pushes score changes to `/topic/matches/live` |
| External APIs | football-data.org v4 (football) + balldontlie.io v1 (NBA) via `RestClient` |
| DTO mapping | Java 21 records + MapStruct |
| Frontend | React 18 + TypeScript 5.5 + Vite 5.4 + Tailwind 3.4 + React Query v5 + @stomp/stompjs |
| Infra | Docker Compose (postgres:16-alpine + redis:7-alpine) |

---

## Package Structure
```
com.onestopsports
├── OneStopSportsApplication.java   @SpringBootApplication @EnableCaching @EnableScheduling
├── config/
│   ├── SecurityConfig.java
│   ├── RedisConfig.java
│   ├── WebSocketConfig.java
│   ├── OpenApiConfig.java          Swagger/OpenAPI setup — JWT Bearer auth scheme for Swagger UI
│   ├── DataLoader.java             Seeds football DB from football-data.org on first boot
│   └── NbaDataLoader.java          Seeds NBA teams + rosters from balldontlie.io on first boot
├── controller/
│   ├── Sport, League, Team, Player, Match, Auth, User, Search controllers
│   └── GlobalExceptionHandler.java @RestControllerAdvice — consistent JSON error responses
├── dto/                            14 Java records (includes ErrorResponseDto, SearchResultDto)
├── model/                          7 JPA entities
├── repository/                     7 JpaRepository interfaces
├── security/
│   ├── JwtUtil.java
│   └── JwtAuthFilter.java
└── service/
    ├── Sport, League, Team, Player, Match, Auth, User services
    ├── ExternalApiService.java     Football API — teams, matches, standings, events (pure football, no scheduler)
    └── NbaApiService.java          NBA API (balldontlie.io) — teams, rosters, scores, standings
```

---

## Key Architecture Decisions Made

### Entities
- Use `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor` — NOT `@Data` on entities (avoids infinite recursion in `toString`/`hashCode` on bidirectional relationships)
- `UserAccount` (not `User`) — `user` is a reserved word in PostgreSQL
- All `@ManyToOne` relationships use `fetch = FetchType.LAZY`
- `@UniqueConstraint` on `FavoriteTeam(user_id, team_id)` and `FavoritePlayer(user_id, player_id)`
- `ON DELETE CASCADE` on favorite tables

### DTOs
- All DTOs are **Java 21 records** (not Lombok classes)
- Inbound request records use Jakarta validation annotations on record components e.g. `@NotBlank String username`
- Jackson deserialises records natively in Spring Boot 3 — no extra config needed

### Security (Spring Security 6)
- `WebSecurityConfigurerAdapter` is **removed** in Spring Security 6 — use `SecurityFilterChain` bean with lambda DSL
- **Circular dependency fix** — the cycle was `JwtAuthFilter → AuthService → PasswordEncoder (in SecurityConfig) → JwtAuthFilter`. Fixed two ways:
  1. `PasswordEncoder` moved to its own `PasswordConfig.java` — keeps `AuthService` completely decoupled from `SecurityConfig`
  2. `AuthenticationManager` injected with `@Lazy` in `AuthService` constructor (manual constructor, not `@RequiredArgsConstructor`) — defers resolution until first `login()` call
- JWT: **jjwt 0.12.x API** — breaking changes from 0.11.x:
  - `Jwts.parser()` not `parserBuilder()`
  - `.verifyWith(key)` not `.setSigningKey(key)`
  - `.parseSignedClaims(token)` not `.parseClaimsJws(token)`
- JWT secret in `application.yml` is Base64-encoded

### GlobalExceptionHandler
- `@RestControllerAdvice` class — catches exceptions thrown anywhere in the controller layer
- Returns consistent `ErrorResponseDto(status, error, message, timestamp)` JSON for all errors
- **Critical:** `ResponseStatusException` MUST have its own `@ExceptionHandler` BEFORE the generic `Exception` catch-all — otherwise the catch-all intercepts it and returns 500 instead of the correct status
- Handles: `MethodArgumentNotValidException` (400), `HttpMessageNotReadableException` (400), `ResponseStatusException` (passthrough), `BadCredentialsException` (401), `AccessDeniedException` (403), `DataIntegrityViolationException` (409), `Exception` (500)

### External APIs
- Uses `RestClient` (Spring 6, synchronous) — **not** `WebClient`
- API keys live in `application-local.yml` (gitignored) — never in `application.yml`
- **Football** (football-data.org): `X-Auth-Token` header auth, 10 req/min free tier → `DataLoader` sleeps 6.2s between competitions
- **NBA** (balldontlie.io): `Authorization: Bearer <key>` header auth, cursor-based pagination (`meta.next_cursor`)

### Multi-Sport Routing
- DB schema is sport-agnostic: `sport → league → team → player`
- `MatchService.getMatchesByLeagueAndDate()` and `LeagueService.getStandings()` check `league.getSport().getSlug()` and route to the correct API:
  - `"basketball"` → `NbaApiService`
  - default → `ExternalApiService` (football)
- Both methods are `@Transactional(readOnly = true)` so the lazy `league.getSport()` relationship loads within a Hibernate session

### NBA Data
- `NbaDataLoader` seeds: 1 Sport (Basketball) → 1 League (NBA) → 30 Teams → full rosters
- Skip condition: checks if all 30 teams already exist — partial seeds resume from where they left off
- `NbaApiService` inner records mirror balldontlie's JSON: `NbaTeam`, `NbaPlayer`, `NbaGame`, `NbaStandingEntry`
- Position mapping: `"G"→"Guard"`, `"F"→"Forward"`, `"C"→"Center"`, `"G-F"→"Guard-Forward"`, `"F-C"→"Forward-Center"`
- NBA teams have no crestUrl or stadium (not on free tier) — frontend handles null gracefully with abbreviation fallback

### WebSocket Live Push
- `MatchService.refreshLiveMatchCache()` runs every 30s via `@Scheduled(fixedDelay = 30_000)` — scheduler moved from `ExternalApiService` to `MatchService` because `MatchService` owns the combined "all sports" live feed
- Fetches football live matches via `ExternalApiService.fetchLiveMatchDtos()` AND NBA live games via `fetchNbaLiveMatches()` (today's games filtered to `status=LIVE`)
- Maintains `previousSnapshot: Map<Long, String>` (matchId → "homeScore:awayScore:status")
- Only pushes when something changes — avoids flooding clients on quiet ticks
- On change: writes fresh data into Redis via `cacheManager.getCache("matches").put(SimpleKey.EMPTY, current)` AND broadcasts via `messagingTemplate.convertAndSend("/topic/matches/live", current)`
- `LeagueRepository.findBySport_Slug("basketball")` — new derived query used to find basketball leagues without a second round-trip through SportRepository
- Frontend: `useLiveScores` hook (`@stomp/stompjs`) subscribes to `/topic/matches/live`, calls `queryClient.setQueryData(["matches","live"], matches)` for instant re-render
- Vite proxy has `ws: true` on `/ws` so WebSocket connections are forwarded to the backend in dev
- REST polling on LivePage reduced to 60s as a fallback — WebSocket is the primary update path
- `getMatchState("LIVE")` added to frontend `types/index.ts` — NBA in-progress games use "LIVE" status (not football's "IN_PLAY"), needed for green score highlighting in MatchCard

### Redis / Jackson
- `RedisConfig` uses a custom `ObjectMapper` with `JavaTimeModule` + `DefaultTyping.EVERYTHING` — the no-arg `GenericJackson2JsonRedisSerializer` uses a bare ObjectMapper that cannot handle `LocalDateTime`, causing 500 when any live match with a `startTime` is cached
- `WebSocketConfig` overrides `configureMessageConverters` to inject Spring Boot's auto-configured `ObjectMapper` — same root cause: the default STOMP converter also creates a bare ObjectMapper

### Global Search
- `GET /api/search?q={query}` (min 2 chars) — returns `SearchResultDto(teams, players)` — up to 8 teams + 10 players
- `TeamRepository.findByNameContainingIgnoreCase` + `PlayerRepository.findByNameContainingIgnoreCase` — Spring Data derived queries generate `LIKE %query%` SQL
- `SearchController` + `SearchResultDto` (new DTO) + `searchTeams`/`searchPlayers` in existing services
- Frontend: `SearchPage` at `/search` with React Query (`enabled: q.length >= 2`), Search nav item added to both `Sidebar` and `BottomNav`

### Swagger / OpenAPI
- `springdoc-openapi-starter-webmvc-ui:2.8.5` — auto-generates docs from `@RestController` classes
- Available at `http://localhost:8080/swagger-ui/index.html`
- `OpenApiConfig.java` adds app title + JWT Bearer auth scheme so locked endpoints can be tested from the UI
- `SecurityConfig` permits `/swagger-ui/**` and `/v3/api-docs/**` without authentication

### Data Seeding (DataLoader — Football)
- `DataLoader implements CommandLineRunner` — runs on every startup, skips if `leagueRepository.count() >= COMPETITION_IDS.length`
- Seeds: 1 Sport (Futbol) → 6 Leagues → up to 20 Teams each → full squads (~1000+ Players)
- Competition IDs: `PL=2021`, `La Liga=2014`, `Bundesliga=2002`, `Serie A=2019`, `Ligue 1=2015`, `UCL=2001`
- Sport name is "Futbol" (not "Football") to distinguish from upcoming NFL addition — slug stays `"football"` so URLs are unaffected

### Build — Annotation Processor Ordering
Lombok MUST come before MapStruct in `maven-compiler-plugin` annotationProcessorPaths, or MapStruct can't see Lombok-generated getters. Use `lombok-mapstruct-binding:0.2.0` as the middle entry.

### Redis
- `GenericJackson2JsonRedisSerializer` used (stores type info in JSON for correct deserialisation)
- Default 30s TTL set programmatically in `RedisConfig` — overrides `application.yml` when using a custom `RedisCacheManager` bean
- Cache key for the no-arg `getLiveMatches()` method is `SimpleKey.EMPTY` — used when manually updating the cache from `refreshLiveMatchCache()`

### Testing
- **`AuthServiceTest`** — pure unit tests with `@ExtendWith(MockitoExtension.class)`, no Spring context
- **`AuthControllerTest`** — `@WebMvcTest` slice tests
  - `@WebMvcTest` only scans web-tier beans — `@Configuration` classes like `SecurityConfig` are NOT auto-scanned. Requires `@Import(SecurityConfig.class)` or Spring's default "deny all" fires and every request returns 401
  - `excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class` prevents duplicate `UserDetailsService` bean crash
  - `spring-security-test` dependency required for `csrf()` / `SecurityMockMvcRequestPostProcessors`

---

## API Response Records
All nested inside `ExternalApiService.java` (football) and `NbaApiService.java` (NBA):

**Football (`ExternalApiService`):**
```
ApiTeamsResponse, ApiCompetition, ApiArea, ApiTeam, ApiCoach, ApiPlayer
ApiMatchesResponse, ApiMatch, ApiMatchTeam, ApiScore, ApiFullTime
ApiStandingsResponse, ApiStandingGroup, ApiStandingEntry
ApiMatchDetail, ApiGoal, ApiBooking, ApiSubstitution, ApiPlayerRef
```

**NBA (`NbaApiService`):**
```
NbaTeamsResponse, NbaTeam, NbaPlayersResponse, NbaMeta, NbaPlayer
NbaGamesResponse, NbaGame, NbaStandingsResponse, NbaStandingEntry
```

---

## Flyway Migrations (all applied)
| File | What it does |
|---|---|
| `V1__create_sport_league.sql` | Creates `sport`, `league` tables |
| `V2__create_team_player.sql` | Creates `team`, `player` tables |
| `V3__create_user_favorites.sql` | Creates `user_account`, `favorite_team`, `favorite_player` tables |
| `V4__add_league_external_id.sql` | Adds `external_id INTEGER` to `league` — bridges DB IDs to football-data.org competition IDs |
| `V5__rename_football_to_futbol.sql` | Renames sport name "Football" → "Futbol" in DB; slug unchanged |

---

## REST API Endpoints

### Public (no auth)
```
GET  /api/sports
GET  /api/sports/{slug}/leagues
GET  /api/leagues/{id}
GET  /api/leagues/{id}/standings
GET  /api/leagues/{id}/teams
GET  /api/teams/{id}
GET  /api/teams/{id}/players
GET  /api/players/{id}
GET  /api/matches?league={id}&date={date}
GET  /api/matches/live                       @Cacheable("matches") — football + NBA combined; also pushed via WebSocket
GET  /api/search?q={query}                   Global search — returns teams + players (min 2 chars, max 8 teams + 10 players)
GET  /api/matches/{id}
GET  /api/matches/{id}/events
GET  /api/matches/{id}/stats                 Returns Map.of() — not in free tier
GET  /api/matches/{id}/lineups               Returns Map.of() — not in free tier
POST /api/auth/register
POST /api/auth/login
```

### Authenticated (JWT required)
```
GET    /api/users/me
GET    /api/users/me/favorites/teams
POST   /api/users/me/favorites/teams
DELETE /api/users/me/favorites/teams/{teamId}
GET    /api/users/me/favorites/players
POST   /api/users/me/favorites/players
DELETE /api/users/me/favorites/players/{playerId}
```

### WebSocket
```
CONNECT  /ws              SockJS endpoint (STOMP over WebSocket)
SUBSCRIBE /topic/matches/live   Server pushes full live match list whenever a score changes
```

---

## Local Dev Setup

### Prerequisites
- PostgreSQL running, database named `onestopsports`
- Redis running on `localhost:6379`
- API keys in `src/main/resources/application-local.yml`:
  ```yaml
  external-api:
    football-data:
      api-key: YOUR_FOOTBALL_KEY
    nba:
      api-key: YOUR_BALLDONTLIE_KEY
  jwt:
    secret: YOUR_BASE64_SECRET
  ```

### Run backend
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Run frontend
```bash
cd frontend && npm run dev
```

### Test (H2 in-memory, no Postgres/Redis needed)
```bash
mvn test
```

### Verify seeding worked
```bash
curl http://localhost:8080/api/sports
curl http://localhost:8080/api/sports/football/leagues
curl http://localhost:8080/api/sports/basketball/leagues
```

### Swagger UI
```
http://localhost:8080/swagger-ui/index.html
```

---

## Current Status

### ✅ Fully implemented
- All 7 JPA entities, 7 repositories, 13 DTOs
- JWT security layer + Spring Security 6 config
- Redis config + WebSocket config
- `GlobalExceptionHandler` — consistent JSON error responses for all error types
- `AuthService` (register + login) + `AuthServiceTest` (6 unit tests)
- `AuthControllerTest` (7 @WebMvcTest slice tests — all passing)
- `UserService` (favorites CRUD — teams + players)
- `SportService`, `LeagueService`, `TeamService`, `PlayerService` (full DB-backed)
- `MatchService`: `getLiveMatches()` (football + NBA combined), `getMatchesByLeagueAndDate()`, `getMatchEvents()`, `getMatchById()`, `refreshLiveMatchCache()` scheduler
- `ExternalApiService` — all football API records, mappers, fetch methods (scheduler moved to MatchService)
- `NbaApiService` — all NBA API records, cursor pagination, `fetchGameDtosByDate`, `fetchStandings`
- `NbaDataLoader` — seeds Basketball sport, NBA league, all 30 teams + rosters
- All 7 REST controllers — all endpoints wired
- `DataLoader` — seeds 6 Futbol leagues, 20 teams each, full squads from football-data.org
- All 5 Flyway migrations applied
- Swagger/OpenAPI at `/swagger-ui/index.html`
- `docker-compose.yml` — postgres:16-alpine + redis:7-alpine with healthchecks
- `.env.example` at project root
- React frontend — 9 pages, 4 components + `useLiveScores` WebSocket hook, JWT Axios interceptor, React Query, Tailwind, responsive layout
- Standings table — color zone indicators (`showZones` prop, no shading for UCL / basketball)
- Multi-sport frontend: Basketball leagues + teams visible alongside Futbol
- `SearchPage` at `/search` — global team + player search, debounced via React Query `enabled`, Search in both nav bars
- Live page shows both football and NBA in-progress games

### 🔲 Stubbed (returns empty — free tier limitation)
- `MatchService.getMatchStats()` — returns `Map.of()` (match stats not in football-data.org free tier)
- `MatchService.getMatchLineups()` — returns `Map.of()` (lineups not in football-data.org free tier)

### 🔲 Not started
- Dockerfile for the Spring Boot app (Docker Compose for infra exists, but the app itself isn't containerised)

---

## Remaining Tasks

### Polish / Nice-to-have
- [ ] Dockerfile for the Spring Boot app + add `app` service to `docker-compose.yml`
- [ ] NFL (American Football) sport — next sport to add after NBA pattern is established
- [ ] Pre-existing TypeScript errors: `PlayerDetailPage.tsx(83)` null type mismatch, `TeamDetailPage.tsx(33)` unused `calculateAge`
