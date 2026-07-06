# Context Intel

Running notes from DOC-type sources, keyed by topic with source attribution.

All five DOC sources are now synthesized (the prior-run cross-reference cycle involving
OVERVIEW.md was broken — ARCHITECTURE.md's `cross_refs` is now empty — so no docs are excluded).

Synthesized DOC sources:
- /Users/james/Projects/OneStopSports/.planning/cowork/CONVENTIONS.md
- /Users/james/Projects/OneStopSports/.planning/cowork/ROADMAP.md
- /Users/james/Projects/OneStopSports/.planning/cowork/INSTRUCTIONS.md
- /Users/james/Projects/OneStopSports/.planning/cowork/OVERVIEW.md
- /Users/james/Projects/OneStopSports/.planning/cowork/HISTORICAL_DATA_RESEARCH.md

---

## Topic: Orientation / what the app is
- source: /Users/james/Projects/OneStopSports/.planning/cowork/OVERVIEW.md
- Fotmob-style multi-sport web app covering three sports: football (soccer) — six leagues seeded from football-data.org (PL, La Liga, Bundesliga, Serie A, Ligue 1, UCL); NBA — 30 teams + full active rosters from ESPN; NFL — 32 teams + ~53-player rosters from ESPN.
- Users browse leagues, view standings, see fixtures/results for any date, drill into a team's squad and an individual player (bio, headshot, career stats), and register/login to favourite teams + players. Live-scores page receives WebSocket pushes the moment a goal is scored.
- Stack at a glance: Java 21 + Spring Boot 3.4.4, Spring 6 `RestClient` (synchronous, not WebFlux), PostgreSQL 16, Flyway (V1-V9), Hibernate/JPA `ddl-auto: validate`, Redis 7 (single `matches` cache, 30s TTL), Spring WebSocket/STOMP (`/topic/matches/live`), Spring Security 6 + JWT (jjwt 0.12.6), Java 21 record DTOs, Lombok quartet entities + MapStruct dependency wired but unused (DTOs hand-mapped), springdoc-openapi 2.8.5 + Swagger UI. Frontend React 18 + TS 5.5 + Vite 5.4, React Query v5 + axios + `@stomp/stompjs`, Tailwind 3.4 + lucide-react. Multi-stage Dockerfile + docker-compose.
- Backend package layout: `com.onestopsports` root + config/controller/dto/model/repository/security/service. 18 DTO records, 7 entities (Sport, League, Team, Player, UserAccount, FavoriteTeam, FavoritePlayer). Business services: Sport/League/Team/Player/Match/Auth/User; external adapters (one per provider): ExternalApiService (football-data.org), NbaApiService, NflApiService (ESPN), BallDontLieService (balldontlie), ApiFootballService (api-sports.io).
- Frontend folder layout: main.tsx, App.tsx (router + QueryClient + Auth/Theme providers), api/ (axios client with Bearer interceptor + per-resource modules), components/, context/ (AuthContext localStorage, ThemeContext light/dark), hooks/ (useLiveScores STOMP), layout/ (AppLayout, Sidebar desktop, BottomNav mobile), lib/ (leagueTheme), pages/ (Home, Live, Leagues, TeamDetail, PlayerDetail, MatchDetail, Auth, Profile, Search), types/index.ts.

## Topic: REST surface (orientation)
- source: /Users/james/Projects/OneStopSports/.planning/cowork/OVERVIEW.md
- Base path `/api`, proxied by Vite to `http://localhost:8081` in dev. All `GET /api/**` is `permitAll`; mutating `/api/users/me/**` requires a Bearer JWT.
- Public: `GET /api/sports`, `/api/sports/{slug}/leagues`, `/api/leagues/{id}[/standings|/teams]`, `/api/teams/{id}[/players]`, `/api/players/{id}` + `/bio` (200 PlayerBioDto | 204) + `/career-stats` (200 | 204), `/api/matches?league=&date=`, `/api/matches/live` (Redis 30s + WS push), `/api/matches/{id}` + `/events` + `/boxscore?leagueId=` (200 BoxScoreDto | 204, sport-routed), `/api/matches/{id}/stats` + `/lineups` (stubbed `{}`, free-tier), `/api/search?q=` (min 2 chars, up to 8 teams + 10 players), `POST /api/auth/register` + `/login`.
- Authenticated (JWT): `GET /api/users/me`; `GET/POST/DELETE /api/users/me/favorites/teams[/{teamId}]`; same for players.
- WebSocket: `CONNECT /ws` (plain WS, no SockJS, Vite-proxied `ws: true`), `SUBSCRIBE /topic/matches/live` (full live-match list on any score/status change).
- Cheatsheet pointers: routing decisions in `MatchService.getMatchesByLeagueAndDate` (~line 97), `LeagueService.getStandings` (~63), `PlayerService.getPlayerCareerStats` (~91); live-push in `MatchService.refreshLiveMatchCache` (~159); seeding in `DataLoader`/`NbaDataLoader`/`NflDataLoader`; migrations in `db/migration/V*__*.sql`.

## Topic: Backend conventions
- source: /Users/james/Projects/OneStopSports/.planning/cowork/CONVENTIONS.md, /Users/james/Projects/OneStopSports/.planning/cowork/INSTRUCTIONS.md
- Package layout: single root `com.onestopsports`, one sub-package per layer (config/controller/dto/model/repository/security/service), no further nesting. Multi-sport logic lives in routing methods, not package boundaries.
- Mandatory plain-English junior-developer inline comments on every new Java file (class/field/method-header/inline rationale/`// -- Section --` dividers). Explain the why.
- DTOs = Java 21 records with `Dto`/`Request` suffixes; validation on record components. Entities = Lombok quartet, never `@Data`; `@ManyToOne` always LAZY.
- Services: one `*Service` per concern, `@Service`, MANUAL constructor injection (not `@RequiredArgsConstructor`), `RestClient` (never `WebClient`), `@Value("${external-api.<provider>.<key>}")`, SLF4J with bracketed prefix.
- Multi-sport routing switches on `sport.getSlug()` strings (never enums/instanceof). Canonical slugs: `"football"` (ExternalApiService), `"basketball"` (NbaApiService), `"american-football"` (NflApiService).
- `@Transactional(readOnly=true)` on read paths that walk lazy chains; plain `@Transactional` on paths that may write (football external_id backfill).
- Repositories: derived queries only, NO `@Query` anywhere. Relationship traversal via underscore (`findBySport_Slug`).
- Exception handling centralised in `GlobalExceptionHandler` (`@RestControllerAdvice`); `ResponseStatusException` handler MUST precede the `Exception` catch-all. External services swallow `RestClientException` -> empty list/null.
- Config: `@Value` constructor injection (never field injection); every `application.yml` key mirrored in `META-INF/additional-spring-configuration-metadata.json`; secrets never in `application.yml`; JWT secret Base64-encoded.
- External-API response records: nested public records under a `// -- API Response Records --` section, each `@JsonIgnoreProperties(ignoreUnknown = true)`, provider-prefixed (`Espn*`/`Api*`/`Bdl*`).
- `pom.xml` annotation-processor order is LOAD-BEARING: Lombok -> lombok-mapstruct-binding -> MapStruct.

## Topic: Frontend conventions
- source: /Users/james/Projects/OneStopSports/.planning/cowork/CONVENTIONS.md, /Users/james/Projects/OneStopSports/.planning/cowork/INSTRUCTIONS.md
- File naming: pages `*Page.tsx`, components `PascalCase.tsx`, layout in `layout/`, hooks `useCamelCase.ts`, api `lowercase.ts`, context `*Context.tsx`, types in a single barrel `types/index.ts`.
- Every backend Java record has a matching TS `interface` (same name, keep `Dto` suffix) in `types/index.ts`; optional/null fields type as `string | null` (backend sends literal null). A new backend record requires a matching interface in the same PR.
- React Query `staleTime` calibrated per data type (live 30s, standings/teams/leagues 5m, favourites 2m, sports 10m, player bio/stats 24h, search 30s). Query keys `[resource, ...identifiers]`. Conditional queries use `enabled`. WebSocket pushes use `queryClient.setQueryData(['matches','live'], ...)`.
- API client: one shared axios instance, `baseURL:'/api'`, request interceptor adds `Authorization: Bearer` from localStorage. Never raw axios in components. 204-capable endpoints use `validateStatus`.
- Tailwind classes literal strings only (JIT purges dynamic). Light default + `dark:` variant on every color class; palette stone/zinc + amber accent; status green/amber/red; `tabular-nums` on scores.
- Functional components only; one default export/file; props interface named `Props`; no `'use client'`. Routing via react-router-dom v6 with `<Link state=...>`; new routes added to BOTH Sidebar and BottomNav.
- Auth context: `useAuth()` is single source of truth for the JWT; token in localStorage; interceptor reads localStorage directly; `useAuth()` throws outside `<AuthProvider>`.
- Design primitives from the "sport field" redesign: `SectionLabel`, `RowCard`/`ROW_DIVIDER`, `SportFieldBackdrop`/`fieldVariantForSport` (variants bowl/court/gridiron), `.glass-card`. Glassmorphism is the intended house style for field-backed surfaces (older "no glassmorphism" rule superseded). New looping animations gated on `prefers-reduced-motion`; decorative SVG `aria-hidden`; keep global `:focus-visible` ring.

## Topic: Critical gotchas
- source: /Users/james/Projects/OneStopSports/.planning/cowork/INSTRUCTIONS.md
- `ResponseStatusException` handler before catch-all (else 404 -> 500). SecurityConfig matcher order load-bearing (`/api/users/me/**` authenticated before broad GET permitAll). Glassmorphism is house style. Accessibility baseline (`:focus-visible`, `prefers-reduced-motion: reduce`) must be preserved. MapStruct processor order load-bearing. jjwt 0.12.x API (not 0.11.x). `UserAccount` not `User`. Redis serializer must be the custom `ObjectMapper` (JavaTimeModule + `DefaultTyping.EVERYTHING`); same for STOMP. OSIV on by default — `toDto` walks three lazy hops only inside a web request. `ddl-auto: validate` — Flyway owns schema; never edit an applied migration, add the next number. `application.yml` carries placeholder secrets only.

## Topic: Tooling
- source: /Users/james/Projects/OneStopSports/.planning/cowork/CONVENTIONS.md, /Users/james/Projects/OneStopSports/.planning/cowork/INSTRUCTIONS.md, /Users/james/Projects/OneStopSports/.planning/cowork/OVERVIEW.md
- Backend run: `mvn spring-boot:run -Dspring-boot.run.profiles=local` (port 8081). Frontend dev: `cd frontend && npm run dev` (port 3000, proxies `/api` + `/ws` to 8081). Tests: `mvn test` (H2 in-memory; Redis disabled via `application-test.yml`; 66 tests across 9 classes; `spring.cache.type: none`). Full stack: `docker-compose up --build` (first boot seeds all three sports ~2 min). Swagger UI: `http://localhost:8081/swagger-ui/index.html` (JWT Bearer scheme wired). Frontend prod build: `cd frontend && npm run build` -> `frontend/dist/`. Local setup Option A = Maven on host + dockerised postgres+redis; Option B = full Docker Compose.

## Topic: Project memory / study guide
- source: /Users/james/Projects/OneStopSports/.planning/cowork/INSTRUCTIONS.md
- Per MEMORY.md: refresh the PDF study guide at project root after each major milestone. Preferred comment style: "explain to a CS student in their first internship".

## Topic: QA remediation status
- source: /Users/james/Projects/OneStopSports/.planning/cowork/ROADMAP.md
- A 5-persona QA pass (football ultra, mobile casual, accessibility consultant, chaos SDET, stats nerd) found and fixed: auth bypass (matcher reorder + 401 AuthenticationEntryPoint), 500s -> 4xx, a11y (`:focus-visible` ring, `prefers-reduced-motion` block), stale-data badge, team-header sport/league resolution. Follow-up commits (5409a3d..2399c3e + V9 dedupe 4956c89) delivered: NBA conference-grouped standings with derived crests, accent-insensitive search (`name_normalized`), winner emphasis + NBA/NFL league logos/crests, a11y (form labels, aria-live, glass contrast, tap targets), structural duplicate-club/player fix (team<->league M:N), server-side PCT/GB columns.

## Topic: Known incomplete features
- source: /Users/james/Projects/OneStopSports/.planning/cowork/ROADMAP.md, /Users/james/Projects/OneStopSports/.planning/cowork/OVERVIEW.md
- Football player headshots GAP: `resolvePhotoUrl` layer-2 fires for NBA/NFL; football lazy-capture from API-Football `player.photo` is NOT wired in `fetchFootballStats` (only external_id saved) — football players show no photo. Fix requires plumbing the photo URL out of `ApiFootballService`.
- Match stats + lineups STUBBED (`Map.of()`) — football-data.org free tier lacks both; controller surface exists so frontend doesn't 404.
- API-Football season cap silently shows 2024-25 data (mid-2026); the CareerStatsTable "most recent available season" note is the only UI signal.
- Push notifications for favourites NOT started (no FCM/APN/service worker infra).
- Production deploy IS set up and public (Vercel + Render + Neon); UptimeRobot 5-min ping mitigates cold starts; the removed `.github/workflows/keep-alive.yml` was throttled too infrequently. Single-origin Docker via `SpaForwardingConfig` still works as a fallback. No CI pipeline yet.

## Topic: Testing gaps
- source: /Users/james/Projects/OneStopSports/.planning/cowork/ROADMAP.md
- 66 backend tests pass, skewed toward auth/MatchService/NbaApiService/LeagueService. Covered: AuthService (6), AuthController (7), MatchService (13), NbaApiService (13), LeagueService (9), PlayerService career-stats routing (9), TeamService (3), TextNormalizer (5), context-load smoke (1). Uncovered: NflApiService, ExternalApiService, ApiFootballService, BallDontLieService, UserService, SportService, PlayerService.toDto/resolvePhotoUrl, JwtUtil/JwtAuthFilter (transitive only), GlobalExceptionHandler, RedisConfig/WebSocketConfig ObjectMapper override, all frontend (no Vitest).
- Highest-value tests to write next: GlobalExceptionHandler, PlayerService.resolvePhotoUrl, ApiFootballService.searchPlayerId, MatchService.refreshLiveMatchCache snapshot-diff, NflApiService standings parsing.

## Topic: External API risks
- source: /Users/james/Projects/OneStopSports/.planning/cowork/ROADMAP.md
- Five external APIs, none fully controlled; three undocumented/unofficial, three free-tier rate-limited. football-data.org 10/min; API-Football 100/day + season cap 2024 + mandatory league filter + single-season (career null) + transfer multi-rows; balldontlie 5/min + first-name search + hyphenated-name fragility; ESPN undocumented (3 subdomains/sport, NBA vs NFL standings subdomain footgun, no retry/circuit breaker, hardcoded NFL division map). Cross-cutting: no central rate-limit awareness, no request observability beyond log.warn.

## Topic: Data model concerns
- source: /Users/james/Projects/OneStopSports/.planning/cowork/ROADMAP.md
- `Player.external_id` soft-typed VARCHAR holding three different ID semantics by sport (no compile-time signal). No external_id uniqueness constraint on player (defended by seed guards). No optimistic locking/`@Version`. `spring.jpa.open-in-view: true` — `toDto` walks team/league/sport outside explicit transaction (OSIV, N+1 risk). `ON DELETE CASCADE` on favourite tables — roster re-seeds silently wipe favourites (accepted for demo).

## Topic: Security concerns
- source: /Users/james/Projects/OneStopSports/.planning/cowork/ROADMAP.md
- CORS/WS lockdown DONE (commit 5eefe79, `app.cors.allowed-origin-patterns`). Still open: JWT placeholder secret in `application.yml` looks real (forgeable if not overridden — Docker/Render use `JWT_SECRET` env); Swagger UI publicly accessible; no rate limiting / account lockout on `/api/auth/login`; no request-size limits / CAPTCHA / email verification; `/ws/**` is `permitAll` with no token verification (fine while data is public); 24h JWT expiry with no refresh/revocation.

## Topic: Operational concerns
- source: /Users/james/Projects/OneStopSports/.planning/cowork/ROADMAP.md
- No server-side cache on ApiFootballService (each uncached view burns 1/100 daily; React Query staleTime is per-tab) or BallDontLieService or `fetchStandings()`. `refreshLiveMatchCache` has no per-call timeout (chains 3 APIs each tick; a hang blocks the scheduler thread). Data loaders swallow exceptions -> partial data on a network blip. `DataLoader` idempotency uses `count() >= COMPETITION_IDS.length` (fragile if a 7th competition is added). No CI — `mvn test` run manually.

## Topic: Priority follow-ups (suggested order)
- source: /Users/james/Projects/OneStopSports/.planning/cowork/ROADMAP.md
- 1) Wire football player photo capture in `fetchFootballStats`. 2) `@Cacheable` (1h TTL) on `ApiFootballService.fetchPlayerStats`. 3) RestClient timeout config on the three ESPN/football services. 4) Test GlobalExceptionHandler + PlayerService.resolvePhotoUrl. 5) Banner on football stats card explaining the 2024-25 cap. 6) Restrict Swagger to dev profile + rotate JWT secret (CORS already locked). 7) Tighten DataLoader idempotency. 8) Set up Vitest on the frontend.

## Topic: Three-sources-of-truth note
- source: /Users/james/Projects/OneStopSports/.planning/cowork/ROADMAP.md
- `PRODUCT.md`, `README.md`, and `CLAUDE.md` all exist at project root — the ROADMAP flags they should be kept aligned or treated as three sources of truth with explicit precedence. (README.md and CLAUDE.md are outside this ingest set; noted for downstream awareness.)

## Topic: Historical data research (future direction)
- source: /Users/james/Projects/OneStopSports/.planning/cowork/HISTORICAL_DATA_RESEARCH.md
- Research (dated 2026-05-26) ranking API options to add historical matchup + stats tracking across all three sports. Current stack covers live/current-season but is blind to anything more than one season old.
- Football options: API-Football Pro ($19/mo, best value — removes the 2024 cap, 7,500 req/day, dedicated head-to-head endpoint, ~2010+ history); football-data.org paid (€29-99/mo, more leagues but no stats depth/h2h); TheStatsAPI ($50/mo, xG + h2h, no free tier); BSD Free Football API (free, undocumented, 2004+ with xG — spike-only, not a foundation); Sportmonks (skip, overkill).
- NBA options: balldontlie paid ($9.99/mo — games/box_scores back to 1946); ESPN unofficial (free, already in stack — untapped `/summary`, `/schedule`, date scoreboard); MySportsFeeds (free for personal/non-commercial — full historical + box scores, ToS limits commercial use).
- NFL options: ESPN unofficial (free, best free option — same untapped `/summary`); balldontlie ($9.99/mo, check tier); MySportsFeeds (free personal use).
- Recommended path: Phase 1 (zero cost) wire ESPN `/summary` for NBA+NFL box scores; Phase 2 ($19/mo) upgrade API-Football to Pro; Phase 3 ($9.99/mo) balldontlie NBA history; Phase 4 MySportsFeeds if staying non-commercial.
- Data model implications: new tables `match_result`, `player_season_stats` (JSON blob per player/season), `team_h2h_cache` (optional materialised cache). Key decision: live match data stays ephemeral (Redis-only); historical results are persisted (never change) and backfilled by a background job.
- What NOT to build on: Sportradar / SportsDataIO at full price (too expensive); BSD as a primary source (too fragile); a Match/Game table for live data (ARCHITECTURE.md calls this an anti-pattern — live data stays ephemeral in Redis).
