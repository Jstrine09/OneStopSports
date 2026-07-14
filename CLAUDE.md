# OneStopSports — Claude Code Context

> **Last refreshed:** reflects commit `ff9fc60` — the app remains **publicly deployed** (Vercel frontend + Render backend) with CORS/WS origins locked down to the real deploy domains. Render's free-tier cold starts are mitigated by an external UptimeRobot monitor (the in-repo GitHub keep-alive workflow was removed — see Infra (prod)). Since `6714a50` ingested the `.planning/` GSD setup, **Phase 1 (Backend Service Test Coverage) and Phase 2 (Postgres Migration Integration Tests) of the "v1 Harden & Test" milestone are both complete** (7/7 plans total): Phase 1 grew the backend suite from 9 test classes/66 tests to 17 test classes/120 tests, closing every previously-untested service (`NflApiService`, `ExternalApiService`, `ApiFootballService`, `BallDontLieService`, `UserService`, `SportService`, `PlayerService` photo/toDto/search, `GlobalExceptionHandler`); Phase 2 added `PostgresMigrationIT` (13 tests, opt-in via `mvn verify -Pintegration`) proving the V8/V9 Flyway migrations against a real Postgres. Two further QA hardening fixes have also landed (`0e0b5f5`, `ff9fc60`): unmapped backend routes now return a clean 404 instead of falling through to the 500 catch-all, and Swagger/OpenAPI (`/v3/api-docs`, `/swagger-ui`) is disabled in the prod profile so the API surface isn't publicly enumerable — the backend suite is now **17 test classes/121 tests**. The milestone is now on **Phase 3 — Frontend Test Foundation** (`status: verifying` → transitioning, 2/4 phases complete, plans TBD). If you're picking this up in a new chat, this file + `.planning/cowork/` + `.planning/ROADMAP.md`/`STATE.md` are the authoritative current-state context.

## Project Overview
**OneStopSports** is a full-stack, Fotmob-style multi-sport app covering **football (soccer), the NBA, and the NFL**. It surfaces live scores (pushed over WebSocket), league standings, match detail + box scores + event timelines, full team rosters, player profiles with **bio, career stats, and headshots**, and global search. Users can register and save favourite teams and players.

**Repo:** `/Users/james/Projects/OneStopSports`
**Branch:** active work happens on `JamesBranch`.
**Related project (reference):** `/Users/james/Projects/OnesToManys` — simpler Spring Boot one-to-many demo using the same football-data.org API; many patterns were ported from it.

---

## Stack
| Layer | Technology |
|---|---|
| Backend | Java 21 + Spring Boot 3.4.4 |
| HTTP port | **8081** (`server.port` in `application.yml`) |
| Database | PostgreSQL 16 (`onestopsports` DB) |
| Migrations | Flyway (**9 migrations**, V1–V9) |
| Cache | Redis 7 (30s TTL on the live-matches cache) |
| Auth | Spring Security 6 + JWT (jjwt 0.12.x) |
| Real-time | Spring WebSocket (STOMP) — server pushes score changes to `/topic/matches/live` |
| External APIs | football-data.org v4 (football) · ESPN unofficial (NBA + NFL) · balldontlie.io (NBA bios) · api-sports.io v3 (football player stats) — all via `RestClient` |
| DTOs | Java 21 records (MapStruct on the build path but DTO mapping is hand-written `toDto`) |
| Frontend | React 18 + TypeScript 5.5 + Vite 5.4 (port 3000) + Tailwind 3.4 + React Query v5 + @stomp/stompjs + lucide-react |
| Infra (dev) | Docker Compose (postgres:16-alpine + redis:7-alpine) |
| Infra (prod) | Split-deploy: frontend on **Vercel** (`frontend/vercel.json` rewrites `/api/*` to Render, same-origin REST, no CORS for REST) + backend on **Render** (`render.yaml`, single-origin Docker still works as a fallback) + Neon Postgres. WS connects straight to Render (`wss://`) since Vercel can't proxy WS upgrades. Free-tier cold starts are mitigated by an **external uptime monitor** (UptimeRobot, 5-min HTTP ping on `/api/sports`, which warms both Render and Neon); the earlier `.github/workflows/keep-alive.yml` was **removed** — GitHub throttles scheduled workflows to ~1–2h apart (too infrequent to keep the instance warm) and its 90s ping was timing out on the cold start and emailing false failures. Frontend is an installable PWA (vite-plugin-pwa, app-shell precache; `/api` + `/ws` excluded from the service worker). |

---

## Package Structure (`com.onestopsports`)
```
OneStopSportsApplication.java     @SpringBootApplication @EnableCaching @EnableScheduling
config/
  SecurityConfig.java             SecurityFilterChain + AuthenticationEntryPoint (401 JSON)
  PasswordConfig.java             PasswordEncoder bean (separated to break a DI cycle)
  RedisConfig.java                RedisCacheManager + custom ObjectMapper (JavaTimeModule)
  WebSocketConfig.java            STOMP /ws + /topic; injects Boot's ObjectMapper
  OpenApiConfig.java              Swagger + JWT bearer scheme
  SpaForwardingConfig.java        Forwards client-side routes to index.html (single-origin prod)
  DataLoader.java                 Seeds football (football-data.org) on boot — hardened: loud failures, resumable, retries
  NbaDataLoader.java              Seeds NBA teams + rosters (ESPN); captures ESPN athlete IDs
  NflDataLoader.java              Seeds NFL teams + rosters (ESPN); captures ESPN athlete IDs
controller/                       Sport, League, Team, Player, Match, Auth, User, Search + GlobalExceptionHandler
dto/                              18 Java records (see list below)
model/                            7 JPA entities (Sport, League, Team, Player, UserAccount, FavoriteTeam, FavoritePlayer)
repository/                       7 JpaRepository interfaces
security/                         JwtUtil, JwtAuthFilter
service/                          12 services (see below)
```

**Services (12):** business — `SportService`, `LeagueService`, `TeamService`, `PlayerService`, `MatchService`, `AuthService`, `UserService`; external-API adapters — `ExternalApiService` (football-data.org), `NbaApiService` (ESPN), `NflApiService` (ESPN), `BallDontLieService` (NBA bios), `ApiFootballService` (api-sports.io football stats).

**DTOs (18):** `SportDto`, `LeagueDto`, `TeamDto`, `PlayerDto`, `PlayerBioDto`, `PlayerCareerStatsDto`, `MatchDto`, `MatchEventDto`, `BoxScoreDto`, `StandingsEntryDto`, `SearchResultDto`, `UserDto`, `ErrorResponseDto`, `AuthRequest`, `AuthResponse`, `RegisterRequest`, `FavoriteTeamRequest`, `FavoritePlayerRequest`. `TeamDto` now has **8 fields** — it gained `leagueIds` (all competitions a club plays in) alongside `leagueId` (the primary league, kept for the team-page header); a 7-arg convenience constructor derives `leagueIds` for synthetic single-league DTOs (standings/box-score teams).

---

## Key Architecture Decisions

### Entities
- `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor` — **never `@Data`** (recursion on bidirectional relationships).
- `UserAccount` (not `User`) — `user` is a PostgreSQL reserved word.
- All `@ManyToOne` are `fetch = FetchType.LAZY`.
- **`Team` ↔ `League` is many-to-many** (V9): a club is ONE row that can belong to several competitions (e.g. Real Madrid in La Liga + the Champions League), via the `team_league` join table (owning side = `Team.leagues`, a LAZY `@ManyToMany`). `Team` also carries a **direct `Team.sport` `@ManyToOne`** (`sport_id`) so sport routing has a single unambiguous answer without a league hop. Helpers: `Team.addLeague(league)` (idempotent) and `Team.getPrimaryLeague()` (smallest-id league = domestic, used for the TeamDto header + the API-Football stats lookup). The football `DataLoader` find-or-creates a club by `(sport_id, external_id)` and links each competition; squads are seeded once per club. **This replaces the old presentation-layer search dedupe — the data is now structurally unique.**
- `@UniqueConstraint` on `FavoriteTeam(user_id, team_id)` and `FavoritePlayer(user_id, player_id)`; `ON DELETE CASCADE` on favourite tables (re-seeding a roster wipes those favourites — accepted trade-off). The V9 merge re-points favourites onto the canonical club/player (respecting the unique constraints) before deleting duplicates.
- `Player.externalId` (V6) and `Team.externalId` (V7): sport-specific external ID. NBA/NFL = ESPN athlete/team ID (set at seed time). Football player = api-sports.io player ID (set lazily on first stats lookup). For football **teams**, `external_id` (the football-data.org team id, identical across competitions) is the find-or-create / merge key for the M:N refactor.

### DTOs
- All Java 21 records. Inbound requests carry Jakarta validation on components (`@NotBlank`, `@Email`, `@Size`).
- `MatchDto` has **10 fields** (id, homeTeam, awayTeam, homeScore, awayScore, status, startTime, leagueId, `timezone`, `clock`). `timezone` = `"ET"` for NBA/NFL else null; `clock` = live game clock (`"3RD · 4:12"` / `"Q3 · 9:22"`) else null.

### Security (Spring Security 6)
- `SecurityFilterChain` bean + lambda DSL (`WebSecurityConfigurerAdapter` is gone in SS6).
- **Matcher ORDER is load-bearing.** `/api/users/me/**` `authenticated()` is declared **before** the broad `GET /api/**` `permitAll()`. (An earlier version had the order reversed → protected GETs matched permitAll first = an auth bypass that only survived via a null-principal NPE→500. Fixed in `bc1a890`.)
- `AuthenticationEntryPoint` returns a clean **401 JSON** envelope for unauthenticated hits on protected endpoints (instead of an empty 403).
- DI cycle (`JwtAuthFilter → AuthService → PasswordEncoder → JwtAuthFilter`) broken by (1) `PasswordConfig` holding the `PasswordEncoder` and (2) `@Lazy AuthenticationManager` in `AuthService`'s manual constructor.
- jjwt **0.12.x** API: `Jwts.parser()` / `.verifyWith(key)` / `.parseSignedClaims(token)`. JWT secret Base64-encoded.
- **CORS/WS origins locked down** (commit `5eefe79`, closes the old "tighten before public deploy" item): `SecurityConfig` (REST CORS) and `WebSocketConfig` (live-scores WS handshake) read a shared `app.cors.allowed-origin-patterns` property (default: localhost + `https://one-stop-sports*.vercel.app`), overridable via `APP_CORS_ALLOWED_ORIGIN_PATTERNS` on Render. The WS check is the one actually exercised cross-origin (browser → Render directly); REST stays same-origin in prod via the Vercel `/api/*` rewrite.
- **Swagger/OpenAPI disabled in prod** (commit `ff9fc60`, QA finding S2): `application-prod.yml` sets `springdoc.api-docs.enabled=false` and `springdoc.swagger-ui.enabled=false` so `/v3/api-docs` and `/swagger-ui` (which enumerate every endpoint + DTO schema) aren't publicly served. Docs remain available on `local`/dev profiles via `application.yml`.

### GlobalExceptionHandler (`@RestControllerAdvice`)
Returns a consistent `ErrorResponseDto(status, error, message, timestamp)` for every error. Handlers:
- `MethodArgumentNotValidException` → 400
- `HttpMessageNotReadableException` → 400 (malformed body)
- `MethodArgumentTypeMismatchException` → 400 *(e.g. `/players/abc`)*
- `MissingServletRequestParameterException` → 400 *(e.g. `/search` with no `q`)*
- `HttpRequestMethodNotSupportedException` → 405
- `BadCredentialsException` → 401 · `AccessDeniedException` → 403
- `ResponseStatusException` → passthrough (**must precede** the `Exception` catch-all)
- `DataIntegrityViolationException` → 409
- `NoResourceFoundException` → 404 *(unmapped backend routes, e.g. `/api/does-not-exist`; Spring Boot 3.2+ throws this instead of falling through to the 500 catch-all — commit `0e0b5f5`)*
- `Exception` → 500

### Multi-Sport Routing
- DB schema is sport-agnostic: `sport → league → team → player`. Per-sport quirks live only in the adapter services.
- Routing switches on the canonical sport slug (`"football"`, `"basketball"`, `"american-football"`). `MatchService.getMatchesByLeagueAndDate` / `LeagueService.getStandings` resolve it via `league.getSport()`. `PlayerService.getPlayerCareerStats` / `PlayerService.resolvePhotoUrl` / `TeamService.getRosterForSeason` resolve it via **`team.getSport()`** (the direct sport link added in V9 — no longer a `team → league → sport` hop, which would be ambiguous now a team has many leagues). Each is `@Transactional`/OSIV so the lazy chain resolves.
- `getMatchById`/`getMatchEvents` are football-only. **Box score IS sport-routed** (`MatchService.getBoxScore(matchId, leagueId)` → `NbaApiService`/`NflApiService.fetchBoxScore` from ESPN's `/summary` endpoint for real NBA/NFL data, or `ExternalApiService.fetchFootballBoxScore` derived from match events for football).

### Player Career Stats / Bio / Photos
- **Career stats** (`GET /api/players/{id}/career-stats`, 200|204): `PlayerService.getPlayerCareerStats` routes by sport → `NbaApiService`/`NflApiService` (ESPN `.../athletes/{espnId}/stats`) or `ApiFootballService` (api-sports.io). One sport-agnostic `PlayerCareerStatsDto` (`categories[] → {labels, seasons[], career}`). NBA/NFL have a career-total row; football is single-season (career row null).
- **Football stats are lazy + capped:** api-sports.io free tier = 100 req/day AND season capped at 2024 (`FREE_TIER_MAX_SEASON`). First lookup searches by name (accent-stripped via `Normalizer`), persists the api-sports player ID to `Player.externalId`, then fetches. `CareerStatsTable` shows a "most recent available season" badge so the lagging season isn't read as current.
- **Bio** (`GET /api/players/{id}/bio`, 200|204): `BallDontLieService` (balldontlie.io) — NBA only (height/weight/college/draft). 204 for football/NFL.
- **Headshots:** `PlayerService.resolvePhotoUrl` — three layers: persisted `photoUrl` → ESPN CDN URL **derived** from `externalId` + sport (`a.espncdn.com/i/headshots/{nba|nfl}/players/full/{id}.png`) → null. No DB column needed for NBA/NFL photos.

### ESPN Data (NBA + NFL)
- ESPN unofficial API, no key. **Three subdomains/paths per sport:** main (`site.api.espn.com/apis/site/v2/...`), standings (NBA: `site.web.api.espn.com/apis/v2`; NFL: `site.api.espn.com/apis/v2`), stats (`site.web.api.espn.com/apis/common/v3`). Footgun: NBA standings use `site.web.api`, NFL use `site.api`.
- NBA roster = flat `athletes[]`; NFL roster = grouped `EspnPositionGroup.items[]` (offense/defense/specialTeam — iterate all). NBA positions are full names + DOB present; NFL are abbreviations + no DOB.
- Times converted UTC→ET: `OffsetDateTime.parse(date).atZoneSameInstant(ZoneId.of("America/New_York")).toLocalDateTime()`. `MatchDto.timezone="ET"`. Live clock from `status.displayClock` + `status.period`.
- Loaders capture ESPN athlete IDs onto `Player.externalId`; re-seed any roster whose players predate that (so career stats work).

### WebSocket Live Push
- `MatchService.refreshLiveMatchCache()` `@Scheduled(fixedDelay=30_000)`: fetches football + NBA + NFL live games, diffs against a `volatile` snapshot map, and only on change writes Redis (`cache.put(SimpleKey.EMPTY, current)`) + broadcasts `/topic/matches/live`.
- Frontend `useLiveScores` hook subscribes and calls `queryClient.setQueryData(['matches','live'], …)`. REST polling at 60s is the fallback. Vite proxy forwards `/ws` with `ws:true`.

### Redis / Jackson / prod cache
- `RedisConfig` uses a custom `ObjectMapper` (`JavaTimeModule` + `DefaultTyping.EVERYTHING`); `WebSocketConfig` injects Boot's auto-configured `ObjectMapper`. Both because the bare default can't serialise `LocalDateTime` → 500s.
- **Redis is dev/local only.** `RedisConfig` is `@Profile("!prod")`. The **prod** profile (`application-prod.yml`) uses Spring's in-memory `cache.type: simple` and excludes the Redis auto-configs — the single prod instance doesn't run Redis. The live-scores scheduler overwrites the cached value every 30s either way, so behaviour matches.

### Frontend Design System — "sport field" redesign (Claude Design handoff)
- **`SportFieldBackdrop`** — portrait playing-field background, variants `bowl` (soccer pitch) / `court` (NBA) / `gridiron` (NFL), with detailed markings, a breathing floodlight glow, and drifting X/O markers. Themed via `currentColor` (a Tailwind text class). `fieldVariantForSport(slug)` maps sport→variant. Used behind: Home league groups, Leagues header + standings/teams panels, Live groups, Match hero, Team header.
- **`.glass-card`** (index.css) — semi-transparent + blur surface so the field reads through tables/rows. **Note: this intentionally introduces glassmorphism, which earlier docs said was "banned" — that rule is superseded; glass is now the house style for field-backed surfaces.**
- **Shared primitives:** `SectionLabel` (uppercase tracked heading) and `RowCard` (+ `ROW_DIVIDER`) — used across Profile/Search/Team and worth reusing on new screens.
- Animations are gated: custom field animation runs only under `prefers-reduced-motion: no-preference`; Tailwind's built-in `animate-pulse/ping/spin` + smooth scroll are disabled under `prefers-reduced-motion: reduce`.
- **Accessibility:** global `:focus-visible` ring (Tailwind Preflight had stripped outlines); decorative backdrops `aria-hidden`. The QA a11y gaps are now closed: AuthPage form labels associated (`htmlFor`/`id`), search input `aria-label`, `aria-live="polite"` on the Live score list, ≥44px tap targets (DateNav arrows, bottom nav), raised `.glass-card` opacity/blur for contrast.
- The field is hidden on phones (`hidden md:block`).

### Search
- `GET /api/search?q=` (min 2 chars) → `SearchResultDto(teams, players)` via `findByNameNormalizedContaining` against the `name_normalized` column (V8). **Accent-insensitive** — "Dembele" matches "Dembélé" (query folded through `TextNormalizer`). **No result de-duplication step** any more — the V9 team↔league refactor makes each club (and its squad) a single row, so a club/player can only match once. (The old `searchTeams`/`searchPlayers` collapse-by-normalized-name has been removed.)

### Build / Seeding
- `pom.xml` annotation-processor order is load-bearing: Lombok → `lombok-mapstruct-binding:0.2.0` → MapStruct.
- `DataLoader` seeds 6 football leagues (PL=2021, La Liga=2014, Bundesliga=2002, Serie A=2019, Ligue 1=2015, UCL=2001), ~20 teams each, full squads; sleeps 6.2s between competitions (10 req/min). Sport name "Futbol", slug `"football"`.

---

## Flyway Migrations (V1–V9, all applied)
| File | What it does |
|---|---|
| V1 `create_sport_league` | `sport`, `league` |
| V2 `create_team_player` | `team`, `player` |
| V3 `create_user_favorites` | `user_account`, `favorite_team`, `favorite_player` (cascade + unique) |
| V4 `add_league_external_id` | `league.external_id` → football-data.org competition IDs |
| V5 `rename_football_to_futbol` | sport name "Football"→"Futbol" (slug unchanged) |
| V6 `add_player_external_id` | `player.external_id` (ESPN athlete ID / api-sports player ID) |
| V7 `add_team_external_id` | `team.external_id` VARCHAR(50) — ESPN team ID (NBA/NFL) / football-data team ID (football); enables historical-roster fetches |
| V8 `add_name_normalized` | `team.name_normalized` + `player.name_normalized` (+ indexes) — accent-stripped, lower-cased names for accent-insensitive search; kept in sync by `@PrePersist/@PreUpdate`, backfilled at boot |
| V9 `team_league_many_to_many` | Creates the `team_league` join table (+ reverse index) and backfills it from `team.league_id`; adds `team.sport_id` (NOT NULL + FK, backfilled from the league's sport); **merges duplicate clubs** sharing `(sport_id, external_id)` into one canonical row (re-pointing players, league links and favourites, then deleting dupes) and de-duplicates the players that the merge brings onto a shared club; finally drops `team.league_id`. Postgres-only (Flyway off in H2 tests). |

---

## REST API Endpoints

### Public
```
GET  /api/sports
GET  /api/sports/{slug}/leagues
GET  /api/leagues/{id}
GET  /api/leagues/{id}/standings
GET  /api/leagues/{id}/teams
GET  /api/teams/{id}
GET  /api/teams/{id}/players
GET  /api/players/{id}
GET  /api/players/{id}/bio              200 PlayerBioDto | 204 (NBA only)
GET  /api/players/{id}/career-stats     200 PlayerCareerStatsDto | 204
GET  /api/matches?league={id}&date={date}
GET  /api/matches/live                  @Cacheable("matches"); also pushed via WebSocket
GET  /api/matches/{id}                   match detail (football)
GET  /api/matches/{id}/events            football match events
GET  /api/matches/{id}/boxscore?leagueId={id}   200 BoxScoreDto | 204 — sport-routed (NBA/NFL real via ESPN /summary; football derived from events)
GET  /api/matches/{id}/stats             stub: {} (free-tier limit)
GET  /api/matches/{id}/lineups           stub: {} (free-tier limit)
GET  /api/search?q={query}              min 2 chars, up to 8 teams + 10 players
POST /api/auth/register
POST /api/auth/login
```
### Authenticated (Bearer JWT)
```
GET    /api/users/me
GET/POST/DELETE  /api/users/me/favorites/teams[/{teamId}]
GET/POST/DELETE  /api/users/me/favorites/players[/{playerId}]
```
### WebSocket
```
CONNECT   /ws                    STOMP over WebSocket
SUBSCRIBE /topic/matches/live    full live-match list on any score/status change
```

---

## Local Dev Setup

**Option A — Maven on host + dockerised infra (fastest loop):**
```bash
docker-compose up -d postgres redis
# secrets → src/main/resources/application-local.yml (gitignored):
#   football-data api-key, balldontlie api-key, api-football api-key, jwt.secret
mvn spring-boot:run -Dspring-boot.run.profiles=local      # backend :8081
cd frontend && npm run dev                                # frontend :3000 (proxies /api + /ws)
```
**Option B — Full Docker Compose:** `cp .env.example .env` (set `DB_PASSWORD`, `FOOTBALL_DATA_API_KEY`, `JWT_SECRET`), then `docker-compose up --build`. First boot seeds all three sports (~2 min).

**Tests:** `mvn test` (H2 in-memory; Redis disabled via `application-test.yml`). The Postgres migration integration test (`PostgresMigrationIT`, verifying the V8 + V9 Flyway migrations against real Postgres) is **opt-in** and does **not** run as part of `mvn test` — run it deliberately with `mvn verify -Pintegration` (or `mvn verify -Pintegration -Dit.test=PostgresMigrationIT` for just that class). It requires a running local Docker daemon (Testcontainers starts an ephemeral `postgres:16-alpine` container; the first run also needs network access to pull the image). If your Docker Desktop rejects Testcontainers' default API-version negotiation ("Could not find a valid Docker environment" despite `docker ps` working), append `-DargLine="-Dapi.version=1.41"` to the command — a known Testcontainers/docker-java gap with newer Docker Desktop `MinAPIVersion` settings, not a project bug.
**Swagger:** `http://localhost:8081/swagger-ui/index.html`.
**Production:** Vercel (frontend, `frontend/vercel.json`) + Render (`render.yaml` + `application-prod.yml`, backend) + Neon Postgres. The single-origin Docker path (`SpaForwardingConfig` serving the built SPA from the backend) still works as a fallback deploy mode but the live deploy is the Vercel/Render split. Free-tier cold starts are fought with an external UptimeRobot monitor (5-min ping on `/api/sports`); the GitHub keep-alive workflow was removed (GitHub throttled the schedule too aggressively to be useful, and false-failed on the slow cold start).

---

## Testing — 121 tests across 17 classes, all green (`mvn test`)
| Class | Count | Notes |
|---|---|---|
| `AuthServiceTest` | 6 | pure unit |
| `AuthControllerTest` | 7 | `@WebMvcTest` + `@Import(SecurityConfig.class)` + `excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class`; needs `spring-security-test` |
| `MatchServiceTest` | 13 | routing + guards; `anyInt()` gotcha for primitive `int` params |
| `NbaApiServiceTest` | 13 | `RETURNS_DEEP_STUBS` RestClient mocks + package-private test constructor; covers per-conference standings grouping + crest derivation |
| `NflApiServiceTest` | 6 | (Phase 01-02) scoreboard/standings/career-stats mapping + soft-fail, mirrors `NbaApiServiceTest`'s fixture-builder + `RETURNS_DEEP_STUBS` convention across 3 RestClients |
| `ExternalApiServiceTest` | 5 | (Phase 01-02) standings + box-score mapping and soft-fail against a mocked RestClient + `LeagueRepository` |
| `ApiFootballServiceTest` | 7 | (Phase 01-03) happy-path + soft-fail; stubs the lambda-based `.uri(Function<UriBuilder,URI>)` call with `any(Function.class)` |
| `BallDontLieServiceTest` | 5 | (Phase 01-03) happy-path + soft-fail; constructs `BdlPlayersResponse`/`BdlPlayer` fixtures directly against the package-private record widening |
| `LeagueServiceTest` | 9 | standings routing |
| `PlayerServiceCareerStatsTest` | 9 | career-stats routing + lazy football ID resolution; helper builds the chain with `Team.sport` + `addLeague` (post-V9) |
| `PlayerServiceTest` | 8 | (Phase 01-05) `resolvePhotoUrl`/`toDto`/`searchPlayers` exercised through the public `getPlayerById` entry point, no reflection |
| `TeamServiceTest` | 3 | team↔league M:N: `toDto` exposes primary `leagueId` + all `leagueIds`, `getTeamsByLeague` uses the join-table query, search no longer collapses by name |
| `UserServiceTest` | 11 | (Phase 01-04) favourites CRUD guards, plain `@InjectMocks`/`@Mock` |
| `SportServiceTest` | 4 | (Phase 01-04) listing + slug lookup, plain `@InjectMocks`/`@Mock` |
| `GlobalExceptionHandlerTest` | 9 | (Phase 01-05) proves `ResponseStatusException`-before-catch-all dispatch order via real MockMvc `standaloneSetup`; also covers the `NoResourceFoundException` → 404 mapping (commit `0e0b5f5`) |
| `TextNormalizerTest` | 5 | accent-folding for accent-insensitive search |
| `OneStopSportsApplicationTests` | 1 | context load; needs `@MockBean RedisConnectionFactory`. Runs the loaders against H2 — also exercises the new `team_league` join + `sport_id` mapping under Hibernate `create-drop`. |

**Remaining coverage gap:** the frontend has zero tests (no Vitest yet — tracked as HARD-03/Phase 3). The **V8/V9 migrations** are now also covered by a real-Postgres integration test — `PostgresMigrationIT` (13 tests: 6 for V8 schema-shape + V9 join/column/FK shape, 7 exhaustive V9 data-merge assertions incl. favourites collision-skip) — run via `mvn verify -Pintegration` (see Tests above); this closed the previous "compile/entity-mapping only" gap (HARD-02/Phase 2).

---

## Current Status

### ✅ Working
Everything in the original build (entities, auth, Redis, WebSocket live push, search, Swagger, Docker) **plus**: player career stats (3 sports) + bio + headshots; live game clock; match box score + mirrored event timeline; the full sport-field/glass frontend redesign across all screens; shared `SectionLabel`/`RowCard` primitives; **public production deploy** (Vercel frontend + Render backend + Neon Postgres, CORS/WS origins locked down, installable PWA, external uptime monitor for cold starts); **full 5-persona QA remediation** — top blockers (auth bypass, 500s→4xx, a11y focus + reduced-motion, stale-data badge) plus all remaining issues: NBA conference-grouped standings, accent-insensitive + deduped search, server-side PCT/GB, NBA/NFL league logos + standings crests, winner emphasis, form labels + aria-live + ≥44px tap targets + glass contrast.

### 🔲 Stubbed (free-tier limits)
- `getMatchStats()` / `getMatchLineups()` → `{}` (football-data.org free tier).
- Football career stats: single season, capped at 2024 (api-sports.io free tier).

### Known issues from the 5-persona QA sweep — ✅ NOW FIXED (commits `5409a3d`–`2399c3e`)
- ✅ **NBA standings group by conference** — `NbaApiService.fetchStandings` ranks within each conference (1–15) and sets `conference`; `StandingsTable` renders East/West as two tables. Crests derived from the team abbreviation via ESPN's CDN.
- ✅ **Accent-insensitive search** — new `name_normalized` column (V8) + `@PrePersist/@PreUpdate` hook on Team/Player + shared `TextNormalizer`; search queries the normalized column so "Dembele" matches "Dembélé". Boot-time `NameNormalizationBackfill` covers pre-existing rows. *(Career-stats name-match misses for some footballers remain — separate api-sports lookup path.)*
- ✅ **Winner emphasised** on finished match cards (loser dimmed, winner bolded); **NBA/NFL league logos** set at seed time from ESPN's league-logo CDN; **standings crests** populated.
- ✅ **A11y**: AuthPage labels associated (`htmlFor`/`id`), search input `aria-label`, `aria-live="polite"` on the Live score list, DateNav arrows + bottom-nav items ≥44px, `.glass-card` opacity/blur raised for contrast.
- ✅ **Duplicate clubs/players** — now fixed **structurally** (see below), superseding the old presentation-layer search dedupe (which has been removed).
- ✅ **PCT/GB columns** added server-side to `StandingsEntryDto` (NBA per-conference, NFL per-division); GB column shown on NBA tables.

**Two further hardening fixes landed after that sweep:**
- ✅ **404 for unmapped backend routes** (commit `0e0b5f5`) — `GlobalExceptionHandler` now maps `NoResourceFoundException` to a clean 404 instead of the 500 catch-all.
- ✅ **Swagger/OpenAPI disabled in prod** (commit `ff9fc60`, QA finding S2) — `/v3/api-docs` and `/swagger-ui` no longer publicly enumerate the API surface in production.

### ✅ Structural dedupe — DONE (team↔league many-to-many, V9)
The tracked follow-up is complete. A club is now a single `team` row that belongs to many competitions via the `team_league` join table, with `Team.sport` as a direct link for routing. The football `DataLoader` find-or-creates each club by `(sport, football-data team id)` and links each competition (squad seeded once); the NBA/NFL loaders set `sport` + link their single league. V9 migrates existing data: it merges duplicate clubs + their duplicated players into canonical rows (re-pointing favourites/links) and drops `team.league_id`. `TeamDto` gained `leagueIds`; `searchTeams`/`searchPlayers` no longer de-duplicate.

### Still open / nice-to-have
- Career-stats 204s for some star footballers (api-sports name-match misses — tracked as HARD-04/Phase 4) · push notifications for favourites · frontend test coverage (zero Vitest tests — tracked as HARD-03/Phase 3) · historical-data tracking (see `.planning/cowork/HISTORICAL_DATA_RESEARCH.md`).
- ✅ **V8/V9 migration coverage gap closed (HARD-02/Phase 2):** both migrations now run against real Postgres via `PostgresMigrationIT` (`mvn verify -Pintegration`) — V9's duplicate-club/player merge and favourites re-point + collision-skip are asserted behaviorally against a seeded duplicate-club fixture, not just validated by compile/entity-mapping.

---

## Context files for a fresh chat
- **This file** (auto-loaded by Claude Code).
- **`.planning/cowork/`** — purpose-built bundle for starting a new Claude/Cowork chat: `PROJECT.md` + `INSTRUCTIONS.md` (paste-in fields), `OVERVIEW`, `ARCHITECTURE`, `INTEGRATIONS`, `CONVENTIONS`, `ROADMAP`, `DECISIONS`, `HISTORICAL_DATA_RESEARCH`.
- **`.planning/codebase/`** — a dated codebase-map snapshot (from 2026-05-21; regenerate with `/gsd:map-codebase` for a fresh analysis).
- **`.planning/` (GSD mode, added `6714a50`)** — `PROJECT.md`, `ROADMAP.md`, `REQUIREMENTS.md`, `STATE.md`, `config.json`, `intel/` (constraints/context/decisions/requirements) and `INGEST-CONFLICTS.md`, ingested from 10 prior planning docs. Tracks a "v1 Harden & Test" milestone (4 phases: backend service test coverage → Postgres migration integration tests → frontend test foundation → career-stats name-match hardening); **Phases 1 and 2 are complete** (2/4 phases, 7/7 plans, HARD-01 + HARD-02 validated), current work is **Phase 3 — Frontend Test Foundation** (HARD-03, not started). Use `/gsd-progress` or similar GSD skills to advance it.
