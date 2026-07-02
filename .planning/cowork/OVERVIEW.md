# OneStopSports — Overview

> **What this doc is:** A one-pager that orients you to OneStopSports — what it does, what it's built with, the package layout, the REST surface, and how to run it locally. Read this first; pull in `ARCHITECTURE.md`, `INTEGRATIONS.md`, `CONVENTIONS.md`, `ROADMAP.md`, or `DECISIONS.md` when you need depth on a specific topic.

---

## What it is

**OneStopSports** is a Fotmob-style multi-sport web app covering three sports through a single experience:

- **Football (soccer)** — six leagues seeded from football-data.org (Premier League, La Liga, Bundesliga, Serie A, Ligue 1, UEFA Champions League)
- **NBA basketball** — 30 teams + full active rosters seeded from ESPN
- **NFL American football** — 32 teams + full ~53-player rosters seeded from ESPN

Users can browse leagues, view standings, see match fixtures and results for any date, drill into a team's squad, drill into an individual player (with bio, headshot, career stats), and register/login to favourite teams and players. The live-scores page receives WebSocket pushes the moment a goal is scored anywhere across the supported leagues.

## Stack at a glance

| Layer | Choice |
|---|---|
| Backend language | **Java 21** |
| Backend framework | **Spring Boot 3.4.4** |
| HTTP client | Spring 6 **`RestClient`** (synchronous; not WebFlux) |
| Database | **PostgreSQL 16** |
| Schema migrations | **Flyway** (nine migrations, `V1`–`V9`) |
| ORM | **Hibernate** via Spring Data JPA; `ddl-auto: validate` |
| Cache | **Redis 7** (single `matches` cache, 30s TTL) |
| Realtime | **Spring WebSocket / STOMP**, topic `/topic/matches/live` |
| Auth | **Spring Security 6** + **JWT** (`jjwt 0.12.6`) |
| DTOs | Java 21 **records** (never Lombok) |
| Entity mapping | **Lombok** quartet + MapStruct dependency wired (currently unused — DTOs are hand-mapped) |
| API docs | **springdoc-openapi 2.8.5** + Swagger UI |
| Frontend | **React 18 + TypeScript 5.5 + Vite 5.4** |
| Frontend state | **`@tanstack/react-query` v5** + `axios` + STOMP via `@stomp/stompjs` |
| Styling | **Tailwind 3.4** + `lucide-react` icons |
| Container target | Multi-stage `Dockerfile`; full stack via `docker-compose.yml` |

## Backend package layout (`src/main/java/com/onestopsports/`)

```
com.onestopsports
├── OneStopSportsApplication.java   @SpringBootApplication @EnableCaching @EnableScheduling
├── config/        @Configuration beans + CommandLineRunner data seeders
├── controller/    @RestController classes + GlobalExceptionHandler
├── dto/           Java 21 records (request + response payloads, 18 total)
├── model/         JPA entities (7 total: Sport, League, Team, Player, UserAccount, FavoriteTeam, FavoritePlayer)
├── repository/    Spring Data JpaRepository interfaces
├── security/      JwtUtil + JwtAuthFilter
└── service/       Business services + one adapter per external API
```

**Service tier breakdown:**
- **Business services**: `SportService`, `LeagueService`, `TeamService`, `PlayerService`, `MatchService`, `AuthService`, `UserService`
- **External-API adapters** (one bean per provider): `ExternalApiService` (football-data.org), `NbaApiService` (ESPN), `NflApiService` (ESPN), `BallDontLieService` (balldontlie), `ApiFootballService` (api-sports.io)

## Frontend folder layout (`frontend/src/`)

```
frontend/src/
├── main.tsx              React DOM root
├── App.tsx               Router + QueryClient + Auth/Theme providers
├── index.css             Tailwind directives
├── api/                  axios client (interceptor injects Bearer token) + per-resource fetch modules
├── components/           Reusable UI — no routing logic
├── context/              AuthContext (token in localStorage), ThemeContext (light/dark)
├── hooks/                useLiveScores (STOMP subscription)
├── layout/               AppLayout, Sidebar (desktop), BottomNav (mobile)
├── lib/                  Pure helpers (leagueTheme)
├── pages/                Route-level views (HomePage, LivePage, LeaguesPage, TeamDetailPage, PlayerDetailPage, MatchDetailPage, AuthPage, ProfilePage, SearchPage)
└── types/                index.ts — every backend record has a matching TS interface here
```

## REST surface

**Base path:** `/api` — proxied through Vite to `http://localhost:8081` in dev. All `GET /api/**` is `permitAll`; mutating endpoints under `/api/users/me/**` require a Bearer JWT.

**Public:**
```
GET  /api/sports
GET  /api/sports/{slug}/leagues
GET  /api/leagues/{id}
GET  /api/leagues/{id}/standings
GET  /api/leagues/{id}/teams
GET  /api/teams/{id}
GET  /api/teams/{id}/players
GET  /api/players/{id}
GET  /api/players/{id}/bio              → 200 PlayerBioDto | 204
GET  /api/players/{id}/career-stats     → 200 PlayerCareerStatsDto | 204
GET  /api/matches?league={id}&date={iso-date}
GET  /api/matches/live                  Cached in Redis 30s; also pushed via /topic/matches/live
GET  /api/matches/{id}
GET  /api/matches/{id}/events
GET  /api/matches/{id}/boxscore?leagueId={id}   200 BoxScoreDto | 204 — sport-routed
GET  /api/matches/{id}/stats            Stubbed → {} (free-tier blocked)
GET  /api/matches/{id}/lineups          Stubbed → {} (free-tier blocked)
GET  /api/search?q={query}              Min 2 chars; up to 8 teams + 10 players
POST /api/auth/register
POST /api/auth/login
```

**Authenticated (JWT required):**
```
GET    /api/users/me
GET    /api/users/me/favorites/teams
POST   /api/users/me/favorites/teams
DELETE /api/users/me/favorites/teams/{teamId}
GET    /api/users/me/favorites/players
POST   /api/users/me/favorites/players
DELETE /api/users/me/favorites/players/{playerId}
```

**WebSocket:**
```
CONNECT   /ws                       Plain WebSocket (no SockJS); proxied by Vite with ws: true
SUBSCRIBE /topic/matches/live       Server pushes the full live-match list on any score/status change
```

## Running it locally

### Option A — Maven on host + dockerised infra (fastest dev loop)

```bash
# 1. Bring up Postgres + Redis only
docker-compose up -d postgres redis

# 2. Add real API keys + JWT secret to application-local.yml (gitignored)
cp .env.example src/main/resources/application-local.yml
# edit: football-data api-key, balldontlie api-key, api-football api-key, jwt.secret

# 3. Backend on port 8081
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 4. Frontend on port 3000 (proxies /api + /ws to 8081)
cd frontend && npm run dev
```

### Option B — Full Docker Compose (prod-like, no host JDK/Node needed)

```bash
cp .env.example .env
# edit: DB_PASSWORD, FOOTBALL_DATA_API_KEY, JWT_SECRET

docker-compose up --build
# First boot seeds all three sports (~2 min)
# App ready at http://localhost:8081 once "Started OneStopSportsApplication" appears
```

### Tests

```bash
mvn test
# 66 tests across 9 test classes; uses H2 + spring.cache.type: none
```

### Swagger UI

```
http://localhost:8081/swagger-ui/index.html
```
JWT Bearer scheme is wired in — paste a token from `POST /api/auth/login` into the "Authorize" dialog and locked endpoints become testable.

## Where things live (cheatsheet)

| Want to... | Look at... |
|---|---|
| See all the REST endpoints | `controller/*Controller.java` |
| Trace a multi-sport routing decision | `MatchService.getMatchesByLeagueAndDate` (line ~97), `LeagueService.getStandings` (line ~63), `PlayerService.getPlayerCareerStats` (line ~91) |
| Find live-score push logic | `MatchService.refreshLiveMatchCache` (`@Scheduled(fixedDelay=30_000)`, line ~159) |
| Understand the security filter chain | `config/SecurityConfig.java` + `security/JwtAuthFilter.java` |
| See how DB seeding works | `config/DataLoader.java` (football), `NbaDataLoader.java`, `NflDataLoader.java` |
| Add/inspect a Flyway migration | `src/main/resources/db/migration/V*__*.sql` |
| Find the JSON error envelope | `controller/GlobalExceptionHandler.java` |
| Read the API config namespace | `application.yml` → `external-api.*` |

## Frontend design system (the "sport field" redesign)
- **`SportFieldBackdrop`** — animated portrait field behind sections, variants `bowl`/`court`/`gridiron` (soccer/NBA/NFL), themed via `currentColor`; `fieldVariantForSport(slug)` picks the variant. Used on Home, Leagues, Live, Match, Team.
- **`.glass-card`** — translucent + blur surface so the field reads through tables/rows. **Glassmorphism is now the house style** for field-backed surfaces (supersedes any older "banned" note).
- **`SectionLabel`** + **`RowCard`** (+ `ROW_DIVIDER`) — shared primitives for section headings and list surfaces; reuse them on new screens.
- A11y baseline: global `:focus-visible` ring + `prefers-reduced-motion` gating; decorative backdrops `aria-hidden`. Field hidden on phones.

## What's not done / known issues
- NBA/NFL player headshots ARE wired (ESPN CDN, derived from `externalId`); **football** headshots are still not captured.
- Football career stats: single season, capped at 2024 (api-sports.io free tier) — UI shows a "most recent available season" badge.
- Match stats + lineups for football: stubbed `{}` (football-data.org free-tier limit).
- No frontend tests (Vitest not configured); backend coverage gaps on `NflApiService`, `ExternalApiService`, `ApiFootballService`, `BallDontLieService`, `UserService`.
- Production deploy IS set up and public: Vercel (frontend) + Render (backend) + Neon (Postgres). Free-tier cold starts mitigated by an external UptimeRobot monitor (5-min ping on `/api/sports`) — the `.github/workflows/keep-alive.yml` was removed (GitHub throttled it too infrequently, 90s curl timed out). Single-origin Docker via `SpaForwardingConfig` still works as a fallback deploy mode. No CI pipeline yet.

See `ROADMAP.md` (top section is the current post-QA state) for the full backlog.
