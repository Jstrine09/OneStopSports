# MatchDay — Claude Code Context

## Project Overview
**MatchDay** is a full-stack sports app inspired by Fotmob. It surfaces live scores, league standings, match timelines, lineups, and player/team stats. Users can create accounts and save favourite teams and players.

**Repo:** `/Users/james/Projects/OneStopSports`
**Related project (for reference):** `/Users/james/Projects/OnesToManys` — a simpler Spring Boot one-to-many demo that uses the same football-data.org API. Many patterns here were ported from it.

---

## Stack
| Layer | Technology |
|---|---|
| Backend | Java 21 + Spring Boot 3.4.4 |
| Database | PostgreSQL (`matchday` DB) |
| Migrations | Flyway (3 migrations done) |
| Cache | Redis (30s TTL on live matches) |
| Auth | Spring Security 6 + JWT (jjwt 0.12.x) |
| Real-time | Spring WebSocket (STOMP) |
| External API | football-data.org v4 via `RestClient` |
| DTO mapping | Java 21 records + MapStruct |
| Frontend | React (not yet started — see backlog) |

---

## Package Structure
```
com.matchday
├── MatchdayApplication.java        @SpringBootApplication @EnableCaching @EnableScheduling
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
- `DataLoader implements CommandLineRunner` — runs on every startup, skips if `sportRepository.count() > 0`
- Seeds: 1 Sport (Football) → 3 Leagues (PL, La Liga, Bundesliga) → ~60 Teams → ~1000 Players
- Competition IDs: `PL=2021`, `La Liga=2014`, `Bundesliga=2002`, `UCL=2001`
- Ported from `/Users/james/Projects/OnesToManys/soccerapp/src/main/java/com/zipcode/soccerapp/config/DataLoader.java`
- MatchDay adds `Sport` as an extra top level that OnesToManys didn't have

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

## Flyway Migrations (completed)
| File | Tables created |
|---|---|
| `V1__create_sport_league.sql` | `sport`, `league` |
| `V2__create_team_player.sql` | `team`, `player` |
| `V3__create_user_favorites.sql` | `user_account`, `favorite_team`, `favorite_player` |

**Next migration needed:** `V4__add_league_external_id.sql` — adds `external_id INTEGER` to `league` table so we can map DB league IDs to football-data.org competition IDs.

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
- PostgreSQL running, database named `matchday`
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

### ✅ Fully scaffolded and implemented
- All 7 JPA entities
- All 7 repositories
- All 12 DTOs
- JWT security layer
- Spring Security 6 config
- Redis + WebSocket config
- `AuthService` (register + login)
- `UserService` (favorites CRUD)
- `SportService`, `LeagueService`, `TeamService`, `PlayerService` (full DB-backed)
- All 7 REST controllers
- `ExternalApiService` — real `RestClient` impl with all response records
- `DataLoader` — seeds real data from football-data.org on first boot
- 3 Flyway migrations

### 🔲 Stubbed (returns empty — needs wiring)
- `MatchService.getMatchesByLeagueAndDate()` — needs `League.externalId` lookup
- `ExternalApiService.fetchStandings()` — needs `ApiStandingEntry → StandingsEntryDto` mapper
- `ExternalApiService.fetchLiveMatchDtos()` — needs `ApiMatch → MatchDto` mapper
- `ExternalApiService.refreshLiveMatchCache()` — needs `SimpMessagingTemplate` push

### 🔲 Not started
- React frontend (Vite + TypeScript + Tailwind + React Query + Recharts)
- `GlobalExceptionHandler`
- Swagger/OpenAPI
- `docker-compose.yml`
- `V4__add_league_external_id.sql`

---

## Kanban — Next Sprint (tasks 31–53)

### Database & Seed
- [ ] 31 Sign up / locate football-data.org API key
- [ ] 32 Add key to `application-local.yml` ✅ done in this session
- [ ] 33 Create local PostgreSQL database `matchday`
- [ ] 34 `mvn spring-boot:run` — confirm Flyway migrations run
- [ ] 35 Confirm DataLoader seeds 1 Sport, 3 Leagues, ~60 Teams, ~1000 Players
- [ ] 36 Add `externalId` (Integer) field to `League.java`
- [ ] 37 Write `V4__add_league_external_id.sql`
- [ ] 38 Update `DataLoader` to store `competitionId` as `league.externalId`
- [ ] 39 Add `findByExternalId(Integer)` to `LeagueRepository`
- [ ] 40 Update `LeagueDto` to include `externalId`

### Live Match Integration
- [ ] 41 Write `ApiMatch → MatchDto` mapper in `ExternalApiService`
- [ ] 42 Implement `fetchLiveMatchDtos()`
- [ ] 43 Test `GET /api/matches/live`
- [ ] 44 Write `ApiStandingEntry → StandingsEntryDto` mapper
- [ ] 45 Implement `LeagueService.getStandings()` via `externalId`
- [ ] 46 Test `GET /api/leagues/{id}/standings`
- [ ] 47 Implement date filtering for matches
- [ ] 48 Implement `getMatchesByLeagueAndDate()` end-to-end
- [ ] 49 Test `GET /api/matches?league=1&date=2025-04-21`

### WebSocket
- [ ] 50 Inject `SimpMessagingTemplate` into `ExternalApiService`
- [ ] 51 Implement score-change detection in `refreshLiveMatchCache()`
- [ ] 52 Push to `/topic/matches/live`
- [ ] 53 Test with Postman WebSocket client

---

## UI Screens (designed, not yet built)
| Screen | Description |
|---|---|
| Home feed | Match cards grouped by league, pill filters (All / Live / EPL / La Liga / UCL) |
| Standings | League table with UCL / UEL / Relegation colour coding |
| Match timeline | Reverse-chronological event list (goals, cards, subs) |
| Match stats | Side-by-side bar charts (shots, possession, passes, discipline) |
| Lineup | Top-down pitch SVG with both formations plotted |
| My Teams | User profile, favourited teams, season stats |
