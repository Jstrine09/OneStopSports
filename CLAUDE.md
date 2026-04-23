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
| Migrations | Flyway (4 migrations — all applied) |
| Cache | Redis (30s TTL on live matches) |
| Auth | Spring Security 6 + JWT (jjwt 0.12.x) |
| Real-time | Spring WebSocket (STOMP — config done, push not yet wired) |
| External API | football-data.org v4 via `RestClient` |
| DTO mapping | Java 21 records + MapStruct |
| Frontend | React 18 + TypeScript 5.5 + Vite 5.4 + Tailwind 3.4 + React Query v5 |
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
│   └── DataLoader.java             Seeds DB from football-data.org on first boot
├── controller/                     Sport, League, Team, Player, Match, Auth, User
├── dto/                            12 Java records
├── model/                          7 JPA entities
├── repository/                     7 JpaRepository interfaces
├── security/
│   ├── JwtUtil.java
│   └── JwtAuthFilter.java
└── service/                        Sport, League, Team, Player, Match, Auth, User, ExternalApi
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

### External API
- Uses `RestClient` (Spring 6, synchronous) — **not** `WebClient` (ported from OnesToManys)
- `WebClient.Builder` is still in `pom.xml` as a dep but not used for the API service
- API key lives in `application-local.yml` (gitignored) — never in `application.yml`
- football-data.org free tier rate limit: 10 req/min → `DataLoader` sleeps 6.2s between competitions

### Data Seeding (DataLoader)
- `DataLoader implements CommandLineRunner` — runs on every startup, skips if `leagueRepository.count() >= COMPETITION_IDS.length`
- Seeds: 1 Sport (Football) → 6 Leagues → up to 20 Teams each → full squads (~1000+ Players)
- Competition IDs: `PL=2021`, `La Liga=2014`, `Bundesliga=2002`, `Serie A=2019`, `Ligue 1=2015`, `UCL=2001`
- Ported from `/Users/james/Projects/OnesToManys/soccerapp/src/main/java/com/zipcode/soccerapp/config/DataLoader.java`
- OneStopSports adds `Sport` as an extra top level that OnesToManys didn't have

### Build — Annotation Processor Ordering
Lombok MUST come before MapStruct in `maven-compiler-plugin` annotationProcessorPaths, or MapStruct can't see Lombok-generated getters. Use `lombok-mapstruct-binding:0.2.0` as the middle entry.

### Redis
- `GenericJackson2JsonRedisSerializer` used (stores type info in JSON for correct deserialisation)
- Default 30s TTL set programmatically in `RedisConfig` — overrides `application.yml` when using a custom `RedisCacheManager` bean

---

## API Response Records
All nested inside `ExternalApiService.java`:
```
ApiTeamsResponse(competition, teams)
ApiCompetition(name, area)
ApiArea(name)
ApiTeam(id, name, shortName, venue, founded, crest, coach, squad)
ApiCoach(name)
ApiPlayer(id, name, position, shirtNumber, nationality, dateOfBirth)
ApiMatchesResponse(matches)
ApiMatch(id, homeTeam, awayTeam, score, status, utcDate, competition)
ApiMatchTeam(id, name, shortName, crest)
ApiScore(fullTime)
ApiFullTime(home, away)
ApiStandingsResponse(competition, standings)
ApiStandingGroup(type, table)
ApiStandingEntry(position, team, playedGames, won, draw, lost, goalsFor, goalsAgainst, points)
```

---

## Flyway Migrations (all applied)
| File | What it does |
|---|---|
| `V1__create_sport_league.sql` | Creates `sport`, `league` tables |
| `V2__create_team_player.sql` | Creates `team`, `player` tables |
| `V3__create_user_favorites.sql` | Creates `user_account`, `favorite_team`, `favorite_player` tables |
| `V4__add_league_external_id.sql` | Adds `external_id INTEGER` to `league` — bridges DB IDs to football-data.org competition IDs |

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
GET  /api/matches/live                       @Cacheable("matches")
GET  /api/matches/{id}
GET  /api/matches/{id}/events
GET  /api/matches/{id}/stats
GET  /api/matches/{id}/lineups
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

---

## Local Dev Setup

### Prerequisites
- PostgreSQL running, database named `onestopsports`
- Redis running on `localhost:6379`
- API key in `src/main/resources/application-local.yml`

### Run
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Test (H2 in-memory, no Postgres/Redis needed)
```bash
mvn test
```

### Verify seeding worked
```bash
curl http://localhost:8080/api/sports
curl http://localhost:8080/api/sports/football/leagues
```

### Register a user
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"james","email":"test@test.com","password":"password1"}'
```

---

## Current Status

### ✅ Fully implemented
- All 7 JPA entities, 7 repositories, 12 DTOs
- JWT security layer + Spring Security 6 config
- Redis config + WebSocket config
- `AuthService` (register + login)
- `UserService` (favorites CRUD — teams + players)
- `SportService`, `LeagueService`, `TeamService`, `PlayerService` (full DB-backed)
- `MatchService`: `getLiveMatches()`, `getMatchesByLeagueAndDate()`, `getMatchEvents()`
- `ExternalApiService` — all API records, all mappers (`toMatchDto`, `toStandingsEntryDto`), all fetch methods wired
- All 7 REST controllers — all endpoints wired and returning real data
- `DataLoader` — seeds 6 leagues, 20 teams each, full squads from football-data.org
- All 4 Flyway migrations applied
- `docker-compose.yml` — postgres:16-alpine + redis:7-alpine with healthchecks
- React frontend — 8 pages, 4 components, JWT Axios interceptor, React Query, Tailwind, responsive layout

### 🔲 Stubbed (returns null/empty)
- `MatchService.getMatchById()` — returns `null` (needs `/matches/{id}` fetch added to `ExternalApiService`)
- `MatchService.getMatchStats()` — returns `Map.of()` (stats not in football-data.org free tier)
- `MatchService.getMatchLineups()` — returns `Map.of()` (lineups not in football-data.org free tier)

### 🔲 Not started
- `ExternalApiService.refreshLiveMatchCache()` — has TODO comment (TASK-17); needs `SimpMessagingTemplate` injection, score-change detection, and push to `/topic/matches/live`
- `GlobalExceptionHandler` — no global error handler yet
- Swagger/OpenAPI — not configured
- Dockerfile for the Spring Boot app (Docker Compose for infra exists, but the app itself isn't containerised)

---

## Remaining Tasks

### Match Detail
- [ ] Add `fetchMatchById(Long id)` to `ExternalApiService` (calls `/matches/{id}`)
- [ ] Wire `MatchService.getMatchById()` to use it
- [ ] Test `GET /api/matches/{id}`

### WebSocket Live Push (TASK-17)
- [ ] Inject `SimpMessagingTemplate` into `ExternalApiService`
- [ ] Implement score-change detection in `refreshLiveMatchCache()`
- [ ] Push diffs to `/topic/matches/live`
- [ ] Test with Postman WebSocket client

### Polish
- [ ] `GlobalExceptionHandler` — consistent JSON error responses
- [ ] Swagger/OpenAPI — auto-generated API docs
- [ ] Dockerfile for the Spring Boot app + add `app` service to `docker-compose.yml`
