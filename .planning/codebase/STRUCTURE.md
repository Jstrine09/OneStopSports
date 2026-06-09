> ⚠️ **SNAPSHOT — 2026-05-21.** This codebase map is a point-in-time analysis and is now partially STALE. For current state see `/CLAUDE.md` and `.planning/cowork/`. Major changes since this snapshot: full "sport field" frontend redesign (`SportFieldBackdrop` + `.glass-card` + `SectionLabel`/`RowCard`), player career stats + bio + ESPN-CDN headshots, live game clock (`MatchDto.clock`, now 10 fields), match box score (`BoxScoreDto`), migrations V6+V7, two new services (`ApiFootballService`, `BallDontLieService`), production deploy (Render + Neon, single-origin via `SpaForwardingConfig`), and the 5-persona QA fixes (auth-bypass fix + `AuthenticationEntryPoint`, 500→4xx handlers, a11y focus/reduced-motion). Tests: 57. Regenerate this map with `/gsd:map-codebase`.

# Codebase Structure

**Analysis Date:** 2026-05-21

## Directory Layout

```
OneStopSports/
├── CLAUDE.md                      # Canonical project context (read FIRST)
├── PRODUCT.md                     # Product overview
├── README.md                      # Repo landing doc
├── Dockerfile                     # Multi-stage build (Maven builder + JRE runtime)
├── docker-compose.yml             # postgres:16 + redis:7 + app
├── pom.xml                        # Maven build (Java 21, Spring Boot 3.4.4, MapStruct, Lombok)
├── skills-lock.json               # Project skills lockfile
├── study_guide_source.html        # Source for OneStopSports_Study_Guide.pdf
├── target/                        # Maven build output (gitignored)
├── src/
│   ├── main/
│   │   ├── java/com/onestopsports/
│   │   │   ├── OneStopSportsApplication.java   # @SpringBootApplication entry point
│   │   │   ├── config/             # Spring @Configuration + CommandLineRunner data seeders
│   │   │   ├── controller/         # @RestController classes + GlobalExceptionHandler
│   │   │   ├── dto/                # Java 21 records (request + response payloads)
│   │   │   ├── model/              # JPA entities
│   │   │   ├── repository/         # Spring Data JPA interfaces
│   │   │   ├── security/           # JWT util + per-request filter
│   │   │   └── service/            # Business services + external-API adapters
│   │   └── resources/
│   │       ├── application.yml          # Default profile (localhost)
│   │       ├── application-local.yml    # Local secrets (gitignored)
│   │       ├── application-docker.yml   # Docker profile (env-driven)
│   │       ├── META-INF/                # Spring Boot auto-config metadata
│   │       └── db/migration/            # Flyway SQL migrations V1..V6
│   └── test/
│       ├── java/com/onestopsports/      # Unit + slice tests (48 total)
│       └── resources/application-test.yml  # Disables Redis for tests
└── frontend/
    ├── package.json               # React 18, Vite 5.4, TS 5.5, Tailwind 3.4, React Query v5
    ├── vite.config.ts             # Dev proxy: /api + /ws → localhost:8081
    ├── tsconfig.json / tsconfig.node.json
    ├── tailwind.config.js / postcss.config.js
    ├── index.html
    └── src/
        ├── main.tsx               # React root
        ├── App.tsx                # BrowserRouter + QueryClientProvider + Auth/Theme providers
        ├── index.css              # Tailwind directives
        ├── api/                   # axios client + per-resource fetch modules
        ├── components/            # Reusable UI components (no routing)
        ├── context/               # React Context providers
        ├── hooks/                 # Custom React hooks
        ├── layout/                # Page chrome (AppLayout + nav)
        ├── lib/                   # Pure helpers (league theming)
        ├── pages/                 # Route-level page components
        └── types/                 # Shared TypeScript types mirroring backend DTOs
```

## Backend Package Purposes

**`src/main/java/com/onestopsports/`**

| Package | Responsibility | Key files |
|---------|----------------|-----------|
| `(root)` | Spring Boot entry point | `OneStopSportsApplication.java` (annotated with `@SpringBootApplication`, `@EnableCaching`, `@EnableScheduling`) |
| `config/` | All `@Configuration` beans and startup data loaders | `SecurityConfig.java`, `PasswordConfig.java`, `RedisConfig.java`, `WebSocketConfig.java`, `OpenApiConfig.java`, `DataLoader.java`, `NbaDataLoader.java`, `NflDataLoader.java` |
| `controller/` | HTTP entry points; thin pass-throughs | `SportController.java`, `LeagueController.java`, `TeamController.java`, `PlayerController.java`, `MatchController.java`, `AuthController.java`, `UserController.java`, `SearchController.java`, `GlobalExceptionHandler.java` |
| `dto/` | Wire-format Java records (request + response) | `SportDto.java`, `LeagueDto.java`, `TeamDto.java`, `PlayerDto.java`, `MatchDto.java`, `MatchEventDto.java`, `StandingsEntryDto.java`, `UserDto.java`, `AuthRequest.java`, `AuthResponse.java`, `RegisterRequest.java`, `FavoriteTeamRequest.java`, `FavoritePlayerRequest.java`, `SearchResultDto.java`, `PlayerBioDto.java`, `PlayerCareerStatsDto.java`, `ErrorResponseDto.java` |
| `model/` | JPA entities | `Sport.java`, `League.java`, `Team.java`, `Player.java`, `UserAccount.java`, `FavoriteTeam.java`, `FavoritePlayer.java` |
| `repository/` | Spring Data JPA interfaces (derived queries only) | `SportRepository.java`, `LeagueRepository.java`, `TeamRepository.java`, `PlayerRepository.java`, `UserRepository.java`, `FavoriteTeamRepository.java`, `FavoritePlayerRepository.java` |
| `security/` | JWT issue/verify + per-request filter | `JwtUtil.java`, `JwtAuthFilter.java` |
| `service/` | Business services + one adapter per external API | `SportService.java`, `LeagueService.java`, `TeamService.java`, `PlayerService.java`, `MatchService.java`, `AuthService.java`, `UserService.java`, `ExternalApiService.java`, `NbaApiService.java`, `NflApiService.java`, `BallDontLieService.java`, `ApiFootballService.java` |

### `config/` deep dive

- `SecurityConfig.java` — `SecurityFilterChain` bean, CORS, route rules, registers `JwtAuthFilter` before `UsernamePasswordAuthenticationFilter`. Exposes `AuthenticationManager` bean from `AuthenticationConfiguration`.
- `PasswordConfig.java` — Standalone `PasswordEncoder` (BCrypt) bean. Lives apart from `SecurityConfig` to break the `JwtAuthFilter → AuthService → PasswordEncoder` circular dependency.
- `RedisConfig.java` — Custom `RedisCacheManager` with `ObjectMapper` that has `JavaTimeModule` + `BasicPolymorphicTypeValidator.allowIfSubType(Object.class)` + `DefaultTyping.EVERYTHING`. 30s TTL.
- `WebSocketConfig.java` — `@EnableWebSocketMessageBroker`. Endpoint `/ws` (plain WS, no SockJS), broker prefix `/topic`, app prefix `/app`. Overrides `configureMessageConverters` to use Boot's auto-configured `ObjectMapper` (otherwise `LocalDateTime` serialisation fails).
- `OpenApiConfig.java` — springdoc `OpenAPI` bean: title + JWT bearer security scheme.
- `DataLoader.java` — `CommandLineRunner` for Futbol (football-data.org). Skip when `leagueRepository.count() >= COMPETITION_IDS.length`. Sleeps 6.2s between competitions (10 req/min limit).
- `NbaDataLoader.java` — `CommandLineRunner` for NBA (ESPN). Seeds 1 Sport, 1 League, 30 teams, full rosters. Idempotent with migration path for old logo-less teams.
- `NflDataLoader.java` — `CommandLineRunner` for NFL (ESPN). Seeds 1 Sport, 1 League, 32 teams, ~53 active players each.

### `service/` deep dive

**Business services (own a slice of domain logic):**
- `SportService.java` — list sports
- `LeagueService.java` — leagues + standings; **routes standings by sport slug**
- `TeamService.java` — teams + search
- `PlayerService.java` — players + bio + **career stats routing by sport slug**
- `MatchService.java` — matches per league/date, live matches, **WebSocket scheduler** (`refreshLiveMatchCache`)
- `AuthService.java` — register/login; implements `UserDetailsService`
- `UserService.java` — current user + favourites CRUD

**External-API adapters (one bean per provider):**
- `ExternalApiService.java` — football-data.org v4 (`X-Auth-Token`, 10 req/min). Inner records: `ApiTeamsResponse`, `ApiCompetition`, `ApiTeam`, `ApiMatchesResponse`, `ApiMatch`, `ApiStandingsResponse`, `ApiMatchDetail`, etc.
- `NbaApiService.java` — ESPN unofficial. Two `RestClient` instances (main + standings subdomain) plus stats URL. ESPN record types nested as `Espn*`.
- `NflApiService.java` — ESPN unofficial. NFL-specific roster grouping (`EspnPositionGroup.items`).
- `BallDontLieService.java` — balldontlie.io for NBA player bios (height, weight, college, draft).
- `ApiFootballService.java` — v3.football.api-sports.io for football season stats. Implements lazy player-ID resolution; saves `externalId` to `Player` on first lookup.

## Frontend Folder Purposes

**`frontend/src/`**

| Folder | Responsibility | Key files |
|--------|----------------|-----------|
| `(root)` | App bootstrap | `main.tsx`, `App.tsx`, `index.css` |
| `api/` | axios client + one module per backend resource | `client.ts` (interceptor attaches `Bearer <token>`), `auth.ts`, `sports.ts`, `leagues.ts`, `teams.ts`, `players.ts`, `matches.ts`, `search.ts` |
| `components/` | Reusable UI — no routing logic | `MatchCard.tsx`, `StandingsTable.tsx`, `CareerStatsTable.tsx`, `DateNav.tsx`, `LoadingSpinner.tsx`, `StadiumBackdrop.tsx`, `ThemeToggle.tsx` |
| `context/` | React Context providers (cross-cutting global state) | `AuthContext.tsx` (token + username in localStorage), `ThemeContext.tsx` (light/dark) |
| `hooks/` | Custom React hooks | `useLiveScores.ts` (STOMP `/topic/matches/live` subscription) |
| `layout/` | Page chrome | `AppLayout.tsx` (Outlet wrapper), `Sidebar.tsx` (desktop), `BottomNav.tsx` (mobile) |
| `lib/` | Pure helpers (no React) | `leagueTheme.ts` (per-league colour zones for standings) |
| `pages/` | Route-level views | `HomePage.tsx`, `LivePage.tsx`, `LeaguesPage.tsx`, `TeamDetailPage.tsx`, `PlayerDetailPage.tsx`, `MatchDetailPage.tsx`, `AuthPage.tsx`, `ProfilePage.tsx`, `SearchPage.tsx` |
| `types/` | TS interfaces mirroring backend DTOs + `getMatchState` helper | `index.ts` |

## Flyway Migrations

Location: **`src/main/resources/db/migration/`** (loaded automatically by `spring.flyway.locations`)

| File | What it does |
|------|--------------|
| `V1__create_sport_league.sql` | Creates `sport` + `league` tables with FK `league.sport_id → sport.id` and index `idx_league_sport_id` |
| `V2__create_team_player.sql` | Creates `team` + `player` tables; FKs `team.league_id → league.id`, `player.team_id → team.id`; indexes on both FK columns |
| `V3__create_user_favorites.sql` | Creates `user_account` (note: `user` is reserved in Postgres), `favorite_team`, `favorite_player`; `ON DELETE CASCADE` + composite `UNIQUE (user_id, team_id)` / `(user_id, player_id)` |
| `V4__add_league_external_id.sql` | Adds `league.external_id INTEGER` (football-data.org competition ID); back-fills PL=2021, Primera Division=2014, Bundesliga=2002 |
| `V5__rename_football_to_futbol.sql` | `UPDATE sport SET name = 'Futbol' WHERE slug = 'football'` — slug intentionally unchanged so URLs and frontend selectors keep working |
| `V6__add_player_external_id.sql` | Adds `player.external_id VARCHAR(64)` + non-unique index `idx_player_external_id`. Meaning is sport-specific (ESPN athlete ID for NBA/NFL; API-Football player ID for soccer, populated lazily) |

DB schema invariant: `spring.jpa.hibernate.ddl-auto: validate` — entity changes without a matching migration fail at boot.

## Key File Locations

**Backend entry points:**
- `src/main/java/com/onestopsports/OneStopSportsApplication.java` — Spring Boot main
- `src/main/java/com/onestopsports/controller/*Controller.java` — REST endpoints
- `src/main/java/com/onestopsports/config/WebSocketConfig.java` — STOMP `/ws` endpoint

**Frontend entry points:**
- `frontend/src/main.tsx` — React DOM root
- `frontend/src/App.tsx` — router + provider tree
- `frontend/src/api/client.ts` — axios singleton

**Configuration:**
- `src/main/resources/application.yml` — default profile + external API URLs + JWT defaults
- `src/main/resources/application-local.yml` — gitignored secrets
- `src/main/resources/application-docker.yml` — Docker profile
- `src/test/resources/application-test.yml` — disables Redis for tests
- `frontend/vite.config.ts` — dev proxy for `/api` and `/ws`
- `pom.xml` — Maven build; annotation processor order Lombok → `lombok-mapstruct-binding` → MapStruct

**Core logic hotspots:**
- `service/MatchService.java` — multi-sport match aggregation + live-score scheduler
- `service/LeagueService.java` — standings routing
- `service/PlayerService.java` — career-stats routing + lazy externalId backfill
- `controller/GlobalExceptionHandler.java` — single source of HTTP-status truth

**Testing:**
- `src/test/java/com/onestopsports/service/` — unit tests (`MatchServiceTest`, `LeagueServiceTest`, `NbaApiServiceTest`, `AuthServiceTest`, `PlayerServiceCareerStatsTest`)
- `src/test/java/com/onestopsports/controller/AuthControllerTest.java` — `@WebMvcTest` slice (needs `@Import(SecurityConfig.class)`)
- `src/test/java/com/onestopsports/OneStopSportsApplicationTests.java` — context-load smoke test

## Naming Conventions

**Files:**
- Java: PascalCase, one public type per file (`MatchService.java`, `MatchDto.java`)
- React: PascalCase `.tsx` for components/pages (`MatchCard.tsx`, `LivePage.tsx`)
- TS modules: camelCase `.ts` (`useLiveScores.ts`, `leagueTheme.ts`)
- Migrations: `V<n>__snake_case_description.sql` (Flyway convention)

**Java types:**
- Entities: singular (`Sport`, `League`, `Team`, `Player`, `UserAccount`)
- DTOs: `<Resource>Dto` (`MatchDto`, `LeagueDto`) — Java 21 records
- Request DTOs: `<Resource>Request` (`AuthRequest`, `RegisterRequest`, `FavoriteTeamRequest`)
- Services: `<Resource>Service` for business services; `<Provider>Service`/`<Provider>ApiService` for external adapters
- Controllers: `<Resource>Controller`
- Repositories: `<Resource>Repository`

**Database:**
- Tables: snake_case singular (`sport`, `league`, `team`, `player`, `user_account`, `favorite_team`, `favorite_player`)
- Columns: snake_case (`external_id`, `crest_url`, `password_hash`, `created_at`)
- Indexes: `idx_<table>_<column>` (`idx_league_sport_id`, `idx_player_external_id`)

**REST paths:** kebab-segments under `/api/<resource>` — `/api/sports/{slug}/leagues`, `/api/leagues/{id}/standings`, `/api/users/me/favorites/teams/{teamId}`.

**Sport slugs (load-bearing):** `"football"` (soccer / Futbol), `"basketball"` (NBA), `"american-football"` (NFL). Switch statements in `MatchService`, `LeagueService`, `PlayerService` key on these literals.

## Where to Add New Code

**A new REST endpoint on an existing resource:**
- Add the method to the matching `<Resource>Controller.java`
- Add (or extend) a method on the matching `<Resource>Service.java`
- Update `SecurityConfig.filterChain` if it isn't a `GET /api/**` (which is already `permitAll`)

**A new business resource (e.g. coaches, fixtures):**
- New entity in `model/` (use the `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor` quartet, lazy `@ManyToOne`)
- New Flyway migration `V<next>__create_<resource>.sql` in `src/main/resources/db/migration/`
- New repository in `repository/`
- New DTO record in `dto/`
- New service in `service/`
- New controller in `controller/`
- Mirror the TS type in `frontend/src/types/index.ts`
- Add a fetch module in `frontend/src/api/` and a page in `frontend/src/pages/`

**A new external API provider:**
- New `@Service` adapter in `service/` (e.g. `<Provider>ApiService.java`)
- Inner records mirroring the provider's JSON (use `@JsonIgnoreProperties(ignoreUnknown = true)`)
- Add URL + (if needed) API key to `application.yml` under `external-api.<provider>` and override in `application-local.yml`
- If the provider corresponds to a new sport: extend the `switch` arms in `MatchService.getMatchesByLeagueAndDate`, `LeagueService.getStandings`, and `PlayerService.getPlayerCareerStats` to cover the new `Sport.slug`
- If live scores are involved: extend `MatchService.fetchNonFootballLiveMatches`
- New `CommandLineRunner` data loader in `config/` if startup seeding is desired

**A new React page:**
- Component in `frontend/src/pages/<Name>Page.tsx`
- Route in `frontend/src/App.tsx`
- Nav item in `frontend/src/layout/Sidebar.tsx` and `BottomNav.tsx` if user-facing
- Reuse `frontend/src/api/<module>.ts` modules; do **not** call axios directly from a page — go through `api/`

**A new reusable React component:**
- `frontend/src/components/<Name>.tsx` — must not import `react-router-dom` (keep routing logic in `pages/`)

**A new cross-cutting frontend concern (auth/theme/etc.):**
- `frontend/src/context/<Name>Context.tsx`, then wrap inside `<AuthProvider>` chain in `App.tsx`

**A new shared frontend helper:**
- Pure logic with no React → `frontend/src/lib/<name>.ts`
- React hook → `frontend/src/hooks/use<Name>.ts`

**A new test:**
- Unit test → `src/test/java/com/onestopsports/service/<Service>Test.java` with `@ExtendWith(MockitoExtension.class)`
- Controller slice test → `src/test/java/com/onestopsports/controller/<Controller>Test.java` with `@WebMvcTest` + `@Import(SecurityConfig.class)` + `excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class`

## Special Directories

**`src/main/resources/db/migration/`:**
- Purpose: Flyway SQL migrations applied automatically at startup
- Generated: No (hand-written)
- Committed: Yes
- Rule: Never edit an existing `V<n>__*.sql` after it has been applied to any environment — always add `V<n+1>`

**`src/test/resources/`:**
- Purpose: Test-only configuration (notably `application-test.yml` disabling Redis)
- Generated: No
- Committed: Yes

**`target/`:**
- Purpose: Maven build output (JAR, compiled classes)
- Generated: Yes (`mvn package`)
- Committed: No

**`frontend/node_modules/`:**
- Purpose: NPM dependencies
- Generated: Yes (`npm install`)
- Committed: No

**`frontend/dist/`:**
- Purpose: Vite production build output
- Generated: Yes (`npm run build`)
- Committed: No

**`.planning/codebase/`:**
- Purpose: GSD codebase-map output (this directory)
- Generated: By `/gsd:map-codebase`
- Committed: Yes (consumed by other GSD commands)

---

*Structure analysis: 2026-05-21*
