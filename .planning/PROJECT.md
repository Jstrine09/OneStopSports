# OneStopSports

## What This Is

OneStopSports is a full-stack, Fotmob-style multi-sport web app that consolidates **football (soccer), the NBA, and the NFL** into one place. It surfaces live scores (pushed over WebSocket), league standings, match detail + box scores + event timelines, full team rosters, player profiles (bio, career stats, headshots), and global search. Registered users can save favourite teams and players.

**This is an existing, already-built and publicly-deployed application** (Vercel frontend + Render backend + Neon Postgres). This planning setup is a reverse-engineered bootstrap: the requirements, decisions, and constraints below describe the CURRENT state of a working app, not greenfield work. The active milestone is **Harden & Test** — de-risking the live app, not rebuilding it.

## Core Value

A user never needs to open a separate app per sport — football (soccer), the NBA, and the NFL live scores/standings/detail are all in one place.

## Business Context

<!-- One-developer teaching/portfolio project; not monetized. -->

- **Customer**: The developer (learning/portfolio) plus casual multi-sport fans using the public deploy.
- **Success metric**: A user never needs to open a separate app per sport (football/soccer + NBA + NFL consolidated).

## Requirements

### Validated

<!-- Shipped, deployed, and working in production. This is the existing-app baseline. -->

- ✓ **Multi-sport consolidation** — football + NBA + NFL in one app (REQ-multi-sport-consolidation)
- ✓ **Live scores** — WebSocket/STOMP push, REST polling fallback (REQ-live-scores)
- ✓ **League standings** — NBA by conference, NFL by division (REQ-standings)
- ✓ **Match detail** — timelines, box scores, live game clock (REQ-match-detail)
- ✓ **Team rosters** — full squads for all three sports (REQ-team-rosters)
- ✓ **Player profiles** — career stats (3 sports), bio (NBA), headshots (NBA/NFL) (REQ-player-profiles)
- ✓ **Favourites** — authenticated users save teams + players (REQ-favourites)
- ✓ **Authentication** — self-hosted username/password + JWT bearer (REQ-authentication)
- ✓ **Global search** — accent-insensitive across teams + players (REQ-search)
- ✓ **Dark-first theme** — purpose-built dark theme, light variant present (REQ-dark-first-theme)
- ✓ **Live feels alive** — motion reinforces real-time updates (REQ-live-feels-alive)
- ✓ **Sport over chrome** — data foregrounded, grouped density (REQ-sport-over-chrome)
- ✓ **WCAG AA accessibility** — contrast + `prefers-reduced-motion` baseline (REQ-accessibility-wcag-aa)
- ✓ **Production deploy** — Vercel + Render + Neon, CORS/WS locked, installable PWA (REQ-production-deploy)
- ✓ **HARD-01** — Backend service test coverage: provider-mocked unit tests for NflApiService, ExternalApiService, ApiFootballService, BallDontLieService, UserService, SportService, PlayerService (`resolvePhotoUrl`/`toDto`/search) + GlobalExceptionHandler dispatch order (Validated in Phase 1; `mvn test` 66→120 tests, all green)

### Active

<!-- Milestone 1: Harden & Test. De-risk the live app. Building toward these. -->

- [ ] **HARD-02**: Add a real Postgres integration test verifying the V8 (`name_normalized`) and V9 (team↔league M:N data merge) Flyway migrations (currently unverified — Flyway is off in H2 tests)
- [ ] **HARD-03**: Establish frontend test coverage (currently zero — no Vitest)
- [ ] **HARD-04**: Fix career-stats 204s for some footballers (api-sports.io name-match misses)

### Out of Scope

<!-- Explicit boundaries for THIS milestone. Captured backlog for later, not scheduled into Milestone 1. -->

- **Historical-data tracking feature** — deferred to a later milestone (research complete in `.planning/cowork/HISTORICAL_DATA_RESEARCH.md`); Harden & Test comes first to de-risk the current app before adding surface area.
- **Push notifications for favourites** — deferred to a later milestone; no FCM/APN/service-worker infra exists yet and it adds new surface, not stability.
- **Rebuilding any already-working feature** — the app is deployed and functional; this milestone hardens and tests existing behaviour, it does not re-implement it.

## Context

**Existing codebase, already deployed.** Full architecture and current-state notes live in `.planning/cowork/` (OVERVIEW, ARCHITECTURE, INTEGRATIONS, CONVENTIONS, ROADMAP, DECISIONS) and in the root `CLAUDE.md`. A dated codebase map is in `.planning/codebase/`.

**Teaching project.** Every new Java file must carry plain-English inline comments aimed at a junior developer (class header, field rationale, inline notes on non-obvious decisions, `// -- Section --` dividers). Preferred voice: "explain to a CS student in their first internship." The PDF study guide at the project root should be refreshed after each major milestone (per user MEMORY.md).

**Test baseline.** 66 backend tests across 9 test classes, all green under `mvn test` (H2 in-memory, Redis disabled via `application-test.yml`, `spring.cache.type: none`). Covered: AuthService (6), AuthController (7), MatchService (13), NbaApiService (13), LeagueService (9), PlayerService career-stats routing (9), TeamService (3), TextNormalizer (5), context-load smoke (1). This is the baseline the Harden & Test milestone builds on — do not regress it.

**Known coverage gaps (the milestone's target).** Uncovered: NflApiService, ExternalApiService, ApiFootballService, BallDontLieService, UserService, SportService, PlayerService.toDto/resolvePhotoUrl, GlobalExceptionHandler, RedisConfig/WebSocketConfig ObjectMapper override, and ALL frontend (no Vitest). The V8 and V9 Flyway migrations run only against real Postgres (Flyway off in H2 tests) and are unverified by any integration test.

**Known functional gap in scope.** Career-stats return 204 for some star footballers because the api-sports.io name-match misses (accent/first-name/lastname edge cases). Fixing this is HARD-04.

**Highest-value tests suggested by the prior roadmap notes:** GlobalExceptionHandler, PlayerService.resolvePhotoUrl, ApiFootballService.searchPlayerId, MatchService.refreshLiveMatchCache snapshot-diff, NflApiService standings parsing.

**Three sources of truth.** `PRODUCT.md`, `README.md`, and `CLAUDE.md` all exist at the project root and should be kept aligned with this planning set (flagged by the ingested ROADMAP.md). When Harden & Test lands changes, update those root docs too.

## Constraints

<!-- Hard limits the roadmap and every plan must respect. These reflect the existing app. -->

- **Tech stack (backend)**: Java 21 + Spring Boot 3.4.4, PostgreSQL 16 via Flyway (V1–V9), Redis 7 (dev-only, `@Profile("!prod")`), Spring Security 6 + JWT (jjwt 0.12.x), Hibernate/JPA `ddl-auto: validate`, Spring WebSocket/STOMP, MapStruct on build path (DTOs hand-mapped), Lombok. HTTP port **8081**.
- **Tech stack (frontend)**: React 18 + TypeScript 5.5 + Vite 5.4, Tailwind 3.4, React Query v5, axios, `@stomp/stompjs`, lucide-react. Port 3000 (proxies `/api` + `/ws`).
- **Deploy**: Vercel (frontend) + Render (backend, Docker) + Neon (Postgres). CORS/WS origins locked to deploy domains. Installable PWA. Cold starts mitigated by an external UptimeRobot ping.
- **DTOs are Java 21 records** — never Lombok classes; Jakarta validation on record components; `Dto`/`Request` suffixes; a matching TS `interface` in `types/index.ts` for every backend record.
- **Entities use the Lombok quartet** `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor` — **never `@Data`** (recurses on bidirectional relationships → StackOverflow). `@ManyToOne` always `FetchType.LAZY`.
- **External-API services use `RestClient`** — never reactive `WebClient`; one `@Service` per provider (never mix providers in one class); manual constructor injection; `@Value("${external-api.<provider>.<key>}")`; nested response records each `@JsonIgnoreProperties(ignoreUnknown = true)`; swallow `RestClientException` → empty list/null.
- **Mandatory junior-developer inline comments** on every new Java file — this is a hard rule, not a style preference (user MEMORY.md).
- **Repositories: derived queries only** — no `@Query` anywhere; relationship traversal via underscore (`findBySport_Slug`).
- **Flyway owns the schema** (`ddl-auto: validate`) — never edit an applied migration; always add the next version number. Entity changes without a matching migration fail at boot.
- **Error handling centralised** in `GlobalExceptionHandler` (`@RestControllerAdvice`); the `ResponseStatusException` handler MUST precede the `Exception` catch-all (else 404 → 500).
- **Security matcher order is load-bearing** — `/api/users/me/**` `authenticated()` declared BEFORE broad `GET /api/**` `permitAll()`.
- **Test env**: `mvn test` runs H2 in-memory with Redis disabled and `spring.cache.type: none`; Flyway is OFF in H2 (this is exactly why V8/V9 need a real-Postgres integration test — HARD-02).
- **Free-tier API limits (real, load-bearing)**: football-data.org 10 req/min, no stats/lineups; api-sports.io 100 req/day + season cap 2024 (`FREE_TIER_MAX_SEASON`); balldontlie 5 req/min, first-name search only; ESPN undocumented (3 subdomains/sport, NBA-vs-NFL standings subdomain footgun). Tests must not depend on live API calls — mock via `RestClient` deep-stubs / package-private test constructors, as the existing NbaApiService tests do.

## Key Decisions

<!-- The 24 ADR decisions, recorded as strong project decisions. Source: .planning/cowork/DECISIONS.md (precedence 0, locked:false — treat as strong, rationale-preserving, not hard-locked-immutable). -->

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| **NBA data from ESPN, not balldontlie** (DEC-espn-over-balldontlie-nba) | ESPN gives free logos + standings, matches the NFL pattern, needs no key; balldontlie scoped to NBA bio only | ✓ Good |
| **api-sports.io for football stats, capped at 2024** (DEC-api-football-and-2024-cap) | football-data.org has no free player stats; free tier is 100/day + season ≤ 2024; serve real stale data over an empty card | ✓ Good (stale-season badge added) |
| **ESPN-CDN headshots derived on the fly** (DEC-espn-cdn-headshots-derived) | Build URL from `external_id` + sport slug; zero new DB columns/API calls; null fallback | ✓ Good |
| **Lazy football external_id lookup** (DEC-lazy-external-id-football) | Pre-seeding ~600 players would burn the 100/day budget; look up on first `/career-stats` and persist | ⚠️ Revisit — unmatched names silently 204 (this milestone's HARD-04) |
| **Accent-strip both sides of name match** (DEC-stripaccents-both-sides) | Fixes Vinícius/Dembélé/Lukáš regardless of which side carries the accent | ⚠️ Revisit — still misses some names (HARD-04) |
| **Route by sport-slug `switch`, not a provider interface** (DEC-sport-slug-switch-routing) | Grep-discoverable; the three routers hit different-signature endpoints; only three sports ever | ✓ Good |
| **Entity named `UserAccount`** (DEC-useraccount-entity-name) | `user` is a Postgres reserved word; REST paths stay `/api/users/...` | ✓ Good |
| **Secrets in gitignored yml/.env** (DEC-secrets-in-gitignored-yml) | One-developer project; standard Spring mechanism; no Vault/Doppler | ⚠️ Revisit — placeholder JWT secret must be overridden in prod |
| **Every DTO is a Java 21 record** (DEC-java-records-dtos) | Free immutability/equality/toString, native Jackson, visually distinct from mutable entities | ✓ Good |
| **Lombok quartet on entities, never `@Data`** (DEC-lombok-quartet-entities) | `@Data` recurses through bidirectional relationships → StackOverflow | ✓ Good |
| **PasswordEncoder in its own PasswordConfig** (DEC-passwordconfig-cycle-break) | Breaks the JwtAuthFilter→AuthService→PasswordEncoder→SecurityConfig cycle; plus `@Lazy AuthenticationManager` | ✓ Good |
| **RestClient, never WebClient** (DEC-restclient-not-webclient) | App is synchronous Servlet; the 30s scheduler has no throughput pressure needing non-blocking I/O | ✓ Good |
| **Custom ObjectMapper for Redis + STOMP** (DEC-custom-objectmapper-redis-stomp) | Bare default can't serialise `LocalDateTime` → silent 500 on any cached match with a startTime | ✓ Good |
| **`timezone` field on MatchDto** (DEC-matchdto-timezone-field) | `"ET"` for NBA/NFL, null for football; ET stored as naive LocalDateTime; frontend appends "ET" | ✓ Good |
| **Keep ESPN's `"LIVE"` status string** (DEC-live-status-strings) | Both LIVE and IN_PLAY map to frontend `'live'`; native value keeps a debugging paper trail | ✓ Good |
| **Hardcoded NFL division map** (DEC-nfl-division-map-hardcoded) | ESPN groups by conference not division; NFL divisions fixed since 2002; avoids an extra API call | ⚠️ Revisit — relocated abbr (OAK→LV) falls to "Unknown Division" |
| **React Query staleTime per query type** (DEC-react-query-staletime-per-type) | Calibrated to upstream change rate (live 30s … player stats 24h), not a global default | ✓ Good |
| **Tailwind literal class strings only** (DEC-tailwind-literal-classes) | JIT purges class names it can't see; `text-${color}` breaks silently in prod | ✓ Good |
| **Mandatory junior-developer inline comments** (DEC-inline-comments-junior) | Teaching project; explain the why (cycle prevention, free-tier limits, ESPN quirks). MANDATORY | ✓ Good |
| **Security matcher order + 401 entrypoint** (DEC-security-matcher-order-entrypoint) | First match wins; reversed order was a real auth bypass; 401 JSON envelope instead of empty 403 | ✓ Good (post-QA `bc1a890`) |
| **500→4xx exception handlers** (DEC-500-to-4xx-handlers) | Type-mismatch/missing-param → 400, wrong-method → 405; ResponseStatusException handler before catch-all | ✓ Good (post-QA) |
| **Accessibility baseline** (DEC-accessibility-baseline) | Global `:focus-visible` ring (Preflight stripped it) + `prefers-reduced-motion: reduce` block; glass is house style | ✓ Good (post-QA) |
| **Football stale-season badge** (DEC-football-stale-season-badge) | "Showing the {season} season — most recent available on the current data plan" note; resolves the 2024-cap honesty gap | ✓ Good (post-QA) |

---
*Last updated: 2026-07-08 — Phase 1 (Backend Service Test Coverage) complete: HARD-01 validated, backend suite grown 66→120 tests (all green). Next: Phase 2 (Postgres migration integration tests, HARD-02).*
