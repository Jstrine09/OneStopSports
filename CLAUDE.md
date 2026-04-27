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
| External APIs | football-data.org v4 (football) + ESPN unofficial API (NBA + NFL) via `RestClient` |
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
│   ├── NbaDataLoader.java          Seeds NBA teams + rosters from ESPN on first boot (migrates old logo-less teams)
│   └── NflDataLoader.java          Seeds NFL teams + rosters from ESPN on first boot
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
    ├── NbaApiService.java          NBA API (ESPN) — teams with logos, rosters, scores, standings
    └── NflApiService.java          NFL API (ESPN) — teams with logos, rosters, scores, standings
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
- **NBA** (ESPN unofficial): no API key needed; base URL `site.api.espn.com/apis/site/v2/sports/basketball/nba`; standings URL `site.web.api.espn.com/apis/v2/sports/basketball/nba/standings`; two `RestClient` instances
- **NFL** (ESPN unofficial): no API key needed; base URL `site.api.espn.com/apis/site/v2/sports/football/nfl`; single `RestClient` instance

### Multi-Sport Routing
- DB schema is sport-agnostic: `sport → league → team → player`
- `MatchService.getMatchesByLeagueAndDate()` and `LeagueService.getStandings()` check `league.getSport().getSlug()` and route to the correct API:
  - `"basketball"` → `NbaApiService`
  - `"american-football"` → `NflApiService`
  - default → `ExternalApiService` (football/soccer)
- Both methods are `@Transactional(readOnly = true)` so the lazy `league.getSport()` relationship loads within a Hibernate session
- `getMatchById()` and `getMatchEvents()` are football-only — they always delegate to `ExternalApiService` (no sport routing needed; NBA/NFL don't expose per-match event APIs)
- `refreshLiveMatchCache()` also queries all basketball leagues via `leagueRepository.findBySport_Slug("basketball")` and all american-football leagues via `findBySport_Slug("american-football")` so live NFL games appear in the combined feed

### NBA Data
- `NbaDataLoader` seeds: 1 Sport (Basketball) → 1 League (NBA) → 30 Teams → full rosters
- Skip condition: all 30 teams exist AND at least one has a crestUrl (ESPN-sourced) — if crestUrls are all null (old data), re-runs to update logos
- `NbaApiService` inner records mirror ESPN's JSON (see API Response Records section)
- NBA ESPN roster: `athletes` is a **flat array** (unlike NFL which groups by offense/defense/specialTeam)
- Positions are already full names from ESPN ("Center", "Guard", "Forward") — no abbreviation-to-full mapping needed
- NBA teams have `crestUrl` from ESPN CDN (e.g. `https://a.espncdn.com/i/teamlogos/nba/500/bos.png`)
- `dateOfBirth` is populated from ESPN (ISO-8601 string parsed to `LocalDate`) — NBA players have DOB
- Standings use `site.web.api.espn.com/apis/v2` (different subdomain from main API) — separate `standingsClient` RestClient in `NbaApiService`
- NBA season year logic: `month >= 10 ? year + 1 : year` (October–December belongs to the next season's label, e.g. Oct 2024 → "2024-25")

### NFL Data
- `NflDataLoader` seeds: 1 Sport (American Football, slug `"american-football"`) → 1 League (NFL) → 32 Teams → full rosters (~53 active players each)
- Skip condition: `teamRepository.findByLeagueId(nfl.getId()).size() >= 32` — if all 32 teams exist, skip entirely; partial seeding fills in missing teams only
- Season label: `"2025-26"`; sleep 1500ms between roster fetches to avoid rate-limiting
- `NflApiService` inner records mirror ESPN's JSON (see API Response Records section)
- NFL rosters are grouped by side: `EspnPositionGroup` has a `position` field (`"offense"` / `"defense"` / `"specialTeam"`) with an `items` list of `EspnAthlete` — must iterate all groups to collect all players
- NFL players do **not** have `dateOfBirth` — ESPN NFL endpoint doesn't return it
- Position abbreviations from ESPN ("QB", "WR", "CB") are stored as-is — no full-name expansion
- NFL teams have `crestUrl` from ESPN CDN (e.g. `https://a.espncdn.com/i/teamlogos/nfl/500/ne.png`)
- NFL season year logic: `month < 9 ? year - 1 : year` (January–August belongs to the previous season, e.g. Feb 2025 → "2024" season)
- Standings: Conference → Division → Group entries (3 levels of nesting); teams sorted by wins descending within each division

### ET Timezone Display (NBA + NFL)
- **Problem**: ESPN returns game times as UTC ISO-8601 strings (e.g. `"2025-04-26T23:30Z"`). The backend stripped the offset with `.toLocalDateTime()`, producing a naive `LocalDateTime`. A browser in Ireland (BST = UTC+1) then shows "11:30 PM" for a "7:30 PM ET" game.
- **Fix**: Convert UTC→ET in both `NbaApiService` and `NflApiService` before building the MatchDto:
  ```java
  startTime = OffsetDateTime.parse(event.date())
          .atZoneSameInstant(ZoneId.of("America/New_York"))
          .toLocalDateTime();
  ```
  `ZoneId.of("America/New_York")` handles EDT/EST transitions automatically.
- **`MatchDto.timezone` field**: 9th field, `String timezone`. `"ET"` for NBA/NFL; `null` for football (soccer). Football times from football-data.org are left as UTC — no conversion needed.
- **Frontend**: `MatchCard.tsx` and `MatchDetailPage.tsx` call `formatKickoff(utc, match.timezone)` — when `timezone === "ET"` the label is appended: `"7:30 PM ET"`. The time string itself displays correctly in any browser locale because the backend already stored the ET wall-clock time as a naive `LocalDateTime`.

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
- **`AuthServiceTest`** — 6 pure unit tests with `@ExtendWith(MockitoExtension.class)`, no Spring context
- **`AuthControllerTest`** — 7 `@WebMvcTest` slice tests
  - `@WebMvcTest` only scans web-tier beans — `@Configuration` classes like `SecurityConfig` are NOT auto-scanned. Requires `@Import(SecurityConfig.class)` or Spring's default "deny all" fires and every request returns 401
  - `excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class` prevents duplicate `UserDetailsService` bean crash
  - `spring-security-test` dependency required for `csrf()` / `SecurityMockMvcRequestPostProcessors`
- **`MatchServiceTest`** — 13 pure unit tests (`@ExtendWith(MockitoExtension.class)`, no Spring context)
  - Tests: null leagueId/date guard clauses, unknown league, basketball→NbaApiService routing, american-football→NflApiService routing, football with/without externalId, getMatchById (null + valid), getMatchEvents (null + valid), getMatchStats/getMatchLineups return empty maps
  - Key gotcha: `fetchMatchDtosByCompetition(int, LocalDate)` takes a primitive `int` — use `anyInt()` not `any()` in `verify()`/`when()`, or Mockito returns `null` which auto-unboxes to NPE
- **`NbaApiServiceTest`** — 12 pure unit tests
  - Uses `@Mock(answer = Answers.RETURNS_DEEP_STUBS) RestClient restClient` — RETURNS_DEEP_STUBS makes every method in a fluent chain return a sub-mock, so `restClient.get().uri(...).retrieve().body(Class)` can be stubbed without every intermediate call matching
  - Manually constructs `NbaApiService` via a **package-private test constructor** in `@BeforeEach` (injects mock RestClients directly, bypassing the `@Value` URL parameter). The production `@Autowired` constructor is marked `@Autowired` so Spring knows which one to use when both exist
  - Tests: null scoreboard response, STATUS_FINAL→FINISHED, STATUS_IN_PROGRESS→LIVE, scheduled games show null scores, leagueId propagation, standings sorted by wins descending, API exception→empty list, draws are always 0 (NBA), played = wins + losses
- **`LeagueServiceTest`** — 9 pure unit tests covering: getStandings 404 on unknown league, basketball/american-football/football routing, football without externalId, getLeagueById found/not found, getLeaguesBySport
- **`OneStopSportsApplicationTests`** — context load test
  - Requires `@MockBean RedisConnectionFactory redisConnectionFactory` — `RedisConfig` creates a `RedisCacheManager` that needs a real `RedisConnectionFactory`; excluding Redis auto-configuration alone is not enough because `RedisConfig` is a user `@Configuration`, not an auto-config
  - `src/test/resources/application-test.yml` sets `spring.cache.type: none` and excludes `RedisAutoConfiguration` + `RedisRepositoriesAutoConfiguration` to prevent Redis from being required during tests
- **Total: 48 tests, all passing** (`mvn test`)

---

## API Response Records
All are private inner records nested inside their respective service class.

**Football (`ExternalApiService`):**
```
ApiTeamsResponse, ApiCompetition, ApiArea, ApiTeam, ApiCoach, ApiPlayer
ApiMatchesResponse, ApiMatch, ApiMatchTeam, ApiScore, ApiFullTime
ApiStandingsResponse, ApiStandingGroup, ApiStandingEntry
ApiMatchDetail, ApiGoal, ApiBooking, ApiSubstitution, ApiPlayerRef
```

**NBA (`NbaApiService`) — ESPN-based:**
```
EspnTeamsResponse, EspnSport, EspnLeague, EspnTeamEntry, EspnTeam, EspnLogo
EspnRosterResponse, EspnAthlete, EspnAthletePosition, EspnBirthPlace
EspnScoreboardResponse, EspnEvent, EspnEventStatus, EspnStatusType, EspnCompetition, EspnCompetitor, EspnCompTeam
EspnStandingsResponse, EspnConference, EspnStandingsSection, EspnStandingsEntry, EspnStandingsTeam, EspnStat
```
NBA-specific: roster `athletes` is a flat array; standings have 2-level nesting (Conference → Entries).

**NFL (`NflApiService`) — ESPN-based (same ESPN API, different sport path):**
```
EspnTeamsResponse, EspnSport, EspnLeague, EspnTeamEntry, EspnTeam, EspnLogo
EspnRosterResponse, EspnPositionGroup, EspnAthlete, EspnAthletePosition, EspnBirthPlace
EspnScoreboardResponse, EspnEvent, EspnEventStatus, EspnStatusType, EspnCompetition, EspnCompetitor, EspnCompTeam
EspnStandingsResponse, EspnConference, EspnDivision, EspnStandingsGroup, EspnStandingsEntry, EspnStandingsTeam, EspnStat
```
NFL-specific differences: `EspnPositionGroup` wraps `items: List<EspnAthlete>` (rosters grouped by offense/defense/specialTeam); standings have 3-level nesting (Conference → Division → Group entries).

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

### Option A — Maven (fastest for active development)
Postgres + Redis via Docker, app runs on the host via Maven.

```bash
# 1. Start infra only
docker-compose up -d postgres redis

# 2. Add secrets to application-local.yml
cp .env.example src/main/resources/application-local.yml
# edit application-local.yml — add football-data api-key and jwt.secret

# 3. Run backend
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 4. Run frontend
cd frontend && npm run dev
```

### Option B — Full Docker Compose (prod-like, no local Java/Maven needed)
Everything in containers — app + Postgres + Redis.

```bash
# 1. Create .env from the example
cp .env.example .env
# edit .env — set FOOTBALL_DATA_API_KEY and JWT_SECRET

# 2. Build and start everything
docker-compose up --build

# On first boot the data loaders seed the DB (~2 min for all three sports).
# The app is ready at http://localhost:8080 once "Started OneStopSportsApplication" appears.
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
curl http://localhost:8080/api/sports/american-football/leagues
```

### Swagger UI
```
http://localhost:8080/swagger-ui/index.html
```

---

## Current Status

### ✅ Fully implemented
- All 7 JPA entities, 7 repositories, 14 DTOs (includes `MatchDto` with `timezone` field)
- JWT security layer + Spring Security 6 config
- Redis config + WebSocket config
- `GlobalExceptionHandler` — consistent JSON error responses for all error types
- `AuthService` (register + login) + `AuthServiceTest` (6 unit tests)
- `AuthControllerTest` (7 `@WebMvcTest` slice tests — all passing)
- `UserService` (favorites CRUD — teams + players)
- `SportService`, `LeagueService`, `TeamService`, `PlayerService` (full DB-backed)
- `MatchService`: `getLiveMatches()` (football + NBA + NFL combined), `getMatchesByLeagueAndDate()`, `getMatchEvents()`, `getMatchById()`, `refreshLiveMatchCache()` scheduler
- `ExternalApiService` — all football API records, mappers, fetch methods (scheduler moved to MatchService)
- `NbaApiService` — ESPN-based: NBA records, `fetchGameDtosByDate`, `fetchStandings`; two RestClient instances (main + standings subdomain); times converted UTC→ET
- `NflApiService` — ESPN-based: NFL records, `fetchAllTeams`, `fetchPlayersByTeam`, `fetchGameDtosByDate`, `fetchStandings`; times converted UTC→ET
- `NbaDataLoader` — seeds Basketball sport, NBA league, 30 teams (ESPN logo URLs) + rosters; auto-migrates teams with missing crestUrls
- `NflDataLoader` — seeds American Football sport, NFL league, 32 teams (ESPN logo URLs) + rosters
- All 7 REST controllers — all endpoints wired
- `DataLoader` — seeds 6 Futbol leagues, 20 teams each, full squads from football-data.org
- All 5 Flyway migrations applied
- Swagger/OpenAPI at `/swagger-ui/index.html`
- `docker-compose.yml` — postgres:16-alpine + redis:7-alpine with healthchecks
- `.env.example` at project root
- React frontend — 9 pages, 4 components + `useLiveScores` WebSocket hook, JWT Axios interceptor, React Query, Tailwind, responsive layout
- Standings table — color zone indicators (`showZones` prop, no shading for UCL / basketball / NFL)
- Multi-sport frontend: Basketball + American Football leagues + teams visible alongside Futbol
- `SearchPage` at `/search` — global team + player search, debounced via React Query `enabled`, Search in both nav bars
- Live page shows football, NBA, and NFL in-progress games
- ET timezone display: NBA/NFL game times shown as "7:30 PM ET" regardless of browser locale
- **48 tests passing** — `MatchServiceTest` (13), `NbaApiServiceTest` (12), `LeagueServiceTest` (9), `AuthServiceTest` (6), `AuthControllerTest` (7), `OneStopSportsApplicationTests` (1)

### 🔲 Stubbed (returns empty — free tier limitation)
- `MatchService.getMatchStats()` — returns `Map.of()` (match stats not in football-data.org free tier)
- `MatchService.getMatchLineups()` — returns `Map.of()` (lineups not in football-data.org free tier)

### ✅ Also implemented
- `Dockerfile` — multi-stage build (Maven builder + JRE runtime); final image has no JDK or source code
- `application-docker.yml` — Docker Spring profile; points datasource to `postgres` service, Redis to `redis` service, reads secrets from env vars
- `docker-compose.yml` — full stack: postgres + redis + app, with `depends_on: service_healthy` so app waits for DB before starting
- `.env.example` updated — documents both local dev (Option A) and full Docker Compose (Option B) workflows
- TypeScript errors fixed: `PlayerDetailPage.tsx` null→undefined, `TeamDetailPage.tsx` unused `calculateAge` removed
- `MatchDto.timezone` field (`String`, 9th component) — `"ET"` for NBA/NFL, `null` for football; both `NbaApiService` and `NflApiService` convert UTC→ET before constructing the DTO
- `src/test/resources/application-test.yml` — disables Redis cache (`spring.cache.type: none`) and excludes Redis auto-configs so tests run without a Redis instance

---

## Remaining Tasks

### Polish / Nice-to-have
- [ ] Push notifications for favourite teams
- [ ] NFL standings display on the frontend (backend `fetchStandings` is implemented; frontend `StandingsTable` needs a conference/division layout instead of a flat table)
- [ ] More test coverage — `NflApiService`, `ExternalApiService`, `UserService`, `TeamService`, `PlayerService` have no unit tests yet
