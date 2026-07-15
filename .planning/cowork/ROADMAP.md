# OneStopSports — Roadmap & Known Concerns

> **What this doc is:** Honest catalog of what's incomplete, what's stubbed, what's risky, and what would be valuable to do next. The app works and demos well, but it leans on free-tier external APIs (several undocumented, several rate-limited) and the test coverage is uneven. Use this when prioritising work.

---

## UPDATE — 5-persona QA pass (current state, commit `bc1a890`)

A QA sweep with 5 user personas (football ultra, mobile casual, accessibility consultant, chaos SDET, stats nerd) ran against the full app. The cheap high-impact **blockers were fixed**:

- **Auth bypass closed** — `SecurityConfig` matchers were reordered so `/api/users/me/**` `authenticated()` is evaluated BEFORE the broad `GET /api/**` `permitAll()` (previously protected GETs slipped through). Added an `AuthenticationEntryPoint` returning a clean 401 JSON envelope. Verified live: `/api/users/me` → 401.
- **500s → 4xx** — `GlobalExceptionHandler` now maps type-mismatch (`/players/abc`) → 400, missing required param (`/search` no `q`) → 400, wrong method → 405. Were all 500s.
- **A11y** — global `:focus-visible` ring (Tailwind Preflight had stripped outlines, WCAG 2.4.7); `prefers-reduced-motion: reduce` now disables Tailwind's built-in `animate-pulse/ping/spin` + smooth scroll (WCAG 2.3.3).
- **Stale-data honesty** — `CareerStatsTable` shows a "most recent available season" badge for football so the lagging single-season feed isn't read as current.
- **Team header** now resolves the team's real sport + league colour via `fetchLeague`/`fetchSports` on any nav path (not just from Leagues).

**UPDATE 2 — all of the above are now fixed (commits `5409a3d`–`2399c3e`, plus the V9 structural dedupe in `4956c89`):** NBA standings group by conference with derived crests; search is accent-insensitive (`name_normalized` column); winner is emphasised on finished cards and NBA/NFL league logos + standings crests are populated; the a11y items (form labels, `aria-live`, glass contrast, tap targets) are done; duplicate clubs/players are now fixed structurally (team↔league many-to-many, not a presentation-layer dedupe); standings DTO has server-side PCT/GB. Remaining open items are tracked in CLAUDE.md's "Still open / nice-to-have" section (career-stats 204s for some footballers, push notifications, more test coverage, historical-data tracking).

The detailed concerns below predate this update but remain accurate except where superseded above.

---

## Known incomplete features

### Football player headshots — ✅ fixed (commit `71e74b7`, QA finding U1)
`PlayerService.resolvePhotoUrl` has three layers: persisted `Player.photoUrl` → a derived CDN URL from `externalId` → `null`. NBA/NFL derive from the ESPN CDN; **football now also derives** from the API-SPORTS media CDN (`media.api-sports.io/football/players/{id}.png`) once `externalId` has been populated by a first career-stats lookup (that lookup still only happens lazily, on the player's stats page — see `fetchFootballStats`). Footballers who've never had their stats viewed still fall through to layer 3 (tracked as HARD-04, a name-match hardening gap, not a missing-wiring gap). The frontend now also renders a graceful initials-tile fallback instead of a broken image icon when no photo resolves.

### Match stats — **stubbed (free-tier-blocked)**
`MatchService.getMatchStats()` returns `Map.of()`. football-data.org's free tier doesn't include match stats. The controller surface exists so the frontend doesn't 404; the UI renders a "coming soon" state.

### Match lineups — **stubbed (free-tier-blocked)**
`MatchService.getMatchLineups()` same story. Free-tier limitation.

### API-Football season cap — **silently shows old data**
Free tier serves season 2024 only. `currentSeason()` is clamped via `FREE_TIER_MAX_SEASON = 2024` in `ApiFootballService.java`. As of mid-2026 this means we're displaying **2024-25 season stats** rather than the in-progress 2025-26 season. No banner in the UI explains this — users may think the data is current.

Fix options: (a) banner on the football player stats card saying "showing 2024-25 — upgrade for current season"; (b) upgrade the API-Football plan and bump the constant; (c) augment with a different paid free-tier soccer API.

### Push notifications for favourite teams — **not started**
Listed as nice-to-have. No infrastructure (no FCM/APN, no service worker).

---

## Testing gaps

**148 backend tests currently pass** via `mvn test` (up from an original 66, across Phase 1 + Phase 2 of the `.planning/` "v1 Harden & Test" milestone plus the post-Phase-2 S1/S3/U1 QA hardening fixes), plus a further 13 opt-in `PostgresMigrationIT` tests (`mvn verify -Pintegration`, requires Docker). Coverage is heavily skewed toward auth, `MatchService`, `NbaApiService`, and `LeagueService`.

| Component | Tests | Notes |
|---|---|---|
| `AuthService` | ✅ 6 | |
| `AuthController` | ✅ 14 (`@WebMvcTest`) | Needs `@Import(SecurityConfig.class)` + `excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class`; covers register/login rate-limiting + refresh cookie, `/refresh`, `/logout`, and the CSP header (QA S1/S3) |
| `AuthRateLimiter` | ✅ 5 (`AuthRateLimiterTest`) | QA S1: pure unit, injected mutable `Clock` proves window reset without sleeping |
| `RefreshTokenService` | ✅ 9 (`RefreshTokenServiceTest`) | QA S3: issue/rotate/revoke incl. reuse detection |
| `PlayerService.resolvePhotoUrl` (dedicated) | ✅ 6 (`PlayerServicePhotoUrlTest`) | QA U1: headshot derivation per sport incl. the new football API-SPORTS CDN layer |
| `MatchService` | ✅ 13 | |
| `NbaApiService` | ✅ 13 | Uses `@Mock(answer = Answers.RETURNS_DEEP_STUBS)` for fluent RestClient chains + package-private test constructor; covers per-conference standings grouping + crest derivation |
| `LeagueService` | ✅ 9 | |
| `PlayerService` (career-stats routing) | ✅ 9 (`PlayerServiceCareerStatsTest`) | Covers routing; does NOT cover `resolvePhotoUrl` or `toDto` |
| `TeamService` | ✅ 3 | Covers the team↔league M:N `toDto` (primary `leagueId` + all `leagueIds`) and the join-table-backed `getTeamsByLeague` |
| `TextNormalizer` | ✅ 5 (`TextNormalizerTest`) | Accent-folding used by accent-insensitive search |
| `OneStopSportsApplicationTests` | ✅ 1 | Context-load smoke; requires `@MockBean RedisConnectionFactory`; also exercises the `team_league` join + `sport_id` mapping under H2 `create-drop` |
| `NflApiService` | ✅ 6 (`NflApiServiceTest`) | Phase 1 (01-02): scoreboard/standings/career-stats mapping + soft-fail, `RETURNS_DEEP_STUBS` across 3 RestClients |
| `ExternalApiService` | ✅ 5 (`ExternalApiServiceTest`) | Phase 1 (01-02): standings + box-score mapping and soft-fail |
| `ApiFootballService` | ✅ 7 (`ApiFootballServiceTest`) | Phase 1 (01-03): happy-path + soft-fail, diacritic/lambda-URI matching covered |
| `BallDontLieService` | ✅ 5 (`BallDontLieServiceTest`) | Phase 1 (01-03): happy-path + soft-fail |
| `UserService` | ✅ 11 (`UserServiceTest`) | Phase 1 (01-04): favourites CRUD guards |
| `SportService` | ✅ 4 (`SportServiceTest`) | Phase 1 (01-04): listing + slug lookup |
| `PlayerService.toDto`/`resolvePhotoUrl`/search | ✅ 8 (`PlayerServiceTest`) | Phase 1 (01-05): exercised through the public `getPlayerById` entry point, `resolvePhotoUrl`'s three-layer logic covered |
| `JwtUtil`, `JwtAuthFilter` | ❌ 0 | Tested transitively via `AuthControllerTest` |
| `GlobalExceptionHandler` | ✅ 9 (`GlobalExceptionHandlerTest`) | Phase 1 (01-05): proves the `ResponseStatusException`-before-catch-all dispatch order via real MockMvc `standaloneSetup`; also covers the `NoResourceFoundException` → 404 mapping added post-Phase-1 (commit `0e0b5f5`) |
| `RedisConfig`, `WebSocketConfig` ObjectMapper override | ❌ 0 | The `LocalDateTime` serialisation fix has no regression test |
| `PostgresMigrationIT` (V8/V9 Flyway migrations) | ✅ 13, opt-in (`mvn verify -Pintegration`) | Phase 2 (02-01/02-02): Testcontainers `postgres:16-alpine`, proves the full V1→V9 chain, V8 name-normalization backfill, and V9's duplicate-club/player merge + favourites re-point/collision-skip against real Postgres (Flyway is off in the H2 unit-test profile, so this was previously compile/entity-mapping-only) |
| Frontend | ❌ 0 | No Vitest setup; TypeScript is the only static check — tracked as HARD-03/Phase 3 in `.planning/` |

All five services and both gaps flagged in the original ranked list below were closed in Phase 1 of the `.planning/` "v1 Harden & Test" milestone (2026-07-08, `mvn test` 66→120 tests across 9→16 classes). Phase 2 (2026-07-13) added the real-Postgres `PostgresMigrationIT` for the V8/V9 migrations, and a post-Phase-1 fix added the `NoResourceFoundException` → 404 test, bringing the suite to 121 tests across 17 classes. Since then, the S1 (rate limiting), S3 (CSP + refresh-token revocation), and U1 (football headshots) QA hardening fixes each landed with their own tests, bringing `mvn test` to **148 tests across 20 classes** (commit `7bcb629`). Remaining gaps: `JwtUtil`/`JwtAuthFilter` direct unit tests, `RedisConfig`/`WebSocketConfig` ObjectMapper regression test, and the frontend test suite (HARD-03/Phase 3, not yet started).

---

## External API risks

The app is a thin orchestration layer over five external APIs. **None are fully under our control.** Three are undocumented/unofficial. Three are free-tier rate-limited.

### football-data.org
- 10 req/min on free tier — `DataLoader` already sleeps 6.2s between seeding calls. Live scheduler ticks every 30s — within budget for now.
- Any user-triggered date browse on the leagues page burns an extra request.

### API-Football (`ApiFootballService`)
- **100 req/day hard cap** — every uncached page view eats one. No server-side cache today; React Query `staleTime` only helps within a single browser tab.
- **Season cap 2024** — silently incorrect for users expecting current-season data (see above).
- League search filter mandatory — anything outside the six-league mapping returns no stats with no UI explanation.
- Single season at a time on free tier — `PlayerCareerStatsDto.career` is null for football. UI parity with NBA/NFL career stats is impossible.
- Mid-season transfers produce multiple rows per player-season; only "competition" disambiguates.

### balldontlie.io
- 5 req/min — easy to trip when clicking through an NBA roster fast. Failures silently hide the bio card.
- First-name-only search — works today but fragile if pagination behaviour changes.
- Hyphenated lastnames stored as one token ("Gilgeous-Alexander") — any tokenisation difference breaks the match.

### ESPN (NBA + NFL)
- **Undocumented, unofficial** — no SLA, no changelog. `@JsonIgnoreProperties(ignoreUnknown = true)` protects against added fields only, not renames or removals.
- **Three subdomains per sport** — NBA standings on `site.web.api.espn.com`, NFL standings on `site.api.espn.com`. Easy footgun.
- No retry / no circuit breaker. If ESPN 500s we log and return an empty list; next 30s tick retries blindly.
- Hardcoded NFL `DIVISION_BY_ABBR` map — if a team relocates and changes abbreviation (OAK → LV in 2020), the team falls into "Unknown Division" until updated.

### Cross-cutting
- **Five APIs × no central rate-limit awareness.** Each service does its own retry-on-failure-by-returning-empty. No coordinated backoff or budget tracking.
- **No request observability.** Beyond `log.warn(...)` no metrics are emitted.

---

## Data model concerns

- **`Player.external_id` is soft-typed.** `VARCHAR(64)` storing three different ID semantics depending on sport (ESPN athlete ID for basketball / american-football; API-Football player ID for football). A maintainer parsing it has no compile-time signal of which API it belongs to — they must walk `player → team → league → sport.slug`. If the matrix grows, consider a `provider` enum column or per-sport columns.
- **No `external_id` uniqueness constraint** on `player`. Two misfiring seed runs could theoretically duplicate rows. Defended in practice by the "skip if seeded" guards in the data loaders.
- **No optimistic locking / `@Version`** on any entity. Concurrent writes to the same favourite row could race. Low-impact for a demo app.
- **`spring.jpa.open-in-view: true`** (default) — `PlayerService.toDto` walks the team/league/sport chain outside any explicit `@Transactional`. It works because OSIV keeps the session open for the web request, but this is a known anti-pattern that can cause N+1 query storms.
- **`ON DELETE CASCADE` on `favorite_player(player_id)` and `favorite_team(team_id)`.** Roster re-seeds (NBA/NFL loaders delete pre-V6 stale rows) **silently wipe users' favourites**. Acceptable for a personal demo; would not be in production.

---

## Security concerns

**UPDATE — the app is now publicly deployed (Vercel + Render + Neon) and most items below are now fixed:** CORS is locked down (commit `5eefe79`) — `SecurityConfig`/`WebSocketConfig` read `app.cors.allowed-origin-patterns` (default: localhost + `https://one-stop-sports*.vercel.app`, overridable via `APP_CORS_ALLOWED_ORIGIN_PATTERNS` on Render) instead of allowing `"*"`. Swagger is now `dev`/`local`-only (commit `ff9fc60`, QA S2). Auth is now rate-limited (commit `9a38f89`, QA S1). JWT expiry/revocation is now hardened (commit `7bcb629`, QA S3) — see CLAUDE.md's "Security (Spring Security 6) → Token model" for the full shape. What's **still open**:

- **JWT placeholder secret in `application.yml`** looks real (`bXlzdXBlcnNlY3JldGtleWZvcmptYXRjaGRheWFwcGxpY2F0aW9u` = "mysupersecretkeyformjmatchdayapplication"). Anyone with the public repo can forge JWTs against any deployment that didn't override it. Docker/Render deployments correctly use a `JWT_SECRET` env var. **Still open** — worth rotating/removing the placeholder now that S1–S3 have closed the other public-facing gaps.
- **No request-size limits, no CAPTCHA on register, no email verification.** Still open.
- **WebSocket `/ws/**` is `permitAll`** with no token verification. Anyone can subscribe to `/topic/matches/live`. Fine today (data is public) — but if user-specific topics are added (`/user/{id}/notifications`), the model needs revisiting. Still open.

**Now fixed (see above):**
- ~~Swagger UI publicly accessible~~ — disabled in prod (`ff9fc60`, QA S2).
- ~~No rate limiting on `/api/auth/login`~~ — per-IP + per-username 429/`Retry-After` (`9a38f89`, QA S1).
- ~~24h JWT expiry, no refresh token, no revocation~~ — replaced with a 15-min access token + httpOnly rotating refresh cookie, DB-backed revocation, and reuse detection (`7bcb629`, QA S3).

---

## Operational concerns

- **No server-side cache on `ApiFootballService`** — every uncached request burns one of 100 daily calls. React Query 24h `staleTime` is per-tab. Two users hitting the same player burn two API calls. A `@Cacheable("player-stats-football")` with a 1-hour TTL would dramatically improve the budget.
- **No server-side cache on `BallDontLieService`** either — same problem on a smaller scale.
- **No cache on `fetchStandings()`** — hits ESPN every page load. Acceptable today; worth a 5-min TTL once traffic grows.
- **`refreshLiveMatchCache` has no per-call timeout.** It chains 3 external APIs each tick. If any one hangs (no `requestFactory()` timeout config on `RestClient`), the entire scheduler thread blocks. One-line fix.
- **All three data loaders swallow exceptions** and let the app start with partial data. A transient network blip during seeding leaves an empty Premier League with no obvious sign — user must re-run.
- **`DataLoader` idempotency uses `count() >= COMPETITION_IDS.length`.** Add a 7th competition and the loader thinks it's already seeded if 6 leagues exist. NBA/NFL loaders use the more robust "all-30-teams + all-have-externalIds" check.
- **No CI configuration** — `mvn test` must be run manually. Nothing enforces it on PR.

---

## Style / maintainability

- **Service class size is creeping up.** `NflApiService` 650 LOC, `NbaApiService` 587, `ExternalApiService` 456. All doing fetching + DTO mapping + standings/scoreboard parsing in one class. Worth splitting if a fourth provider per sport ever lands.
- **Duplicated patterns across NBA/NFL services** — ET timezone conversion (`OffsetDateTime.parse(...).atZoneSameInstant(...)`), the three-RestClient-per-service shape, ESPN status code → status enum mapping. A shared `EspnTimezoneUtil` or `EspnStatusMapper` would DRY this up.
- **Inner records `EspnXxx` collide across services.** Both `NbaApiService` and `NflApiService` declare `EspnTeamsResponse`, `EspnSport`, `EspnLeague`, etc. — package-private so no compile clash, but `grep`'ing for `EspnAthlete` hits two files with slightly different shapes.
- **`MatchService.fetchNonFootballLiveMatches`** calls `findBySport_Slug` twice — a `List.of("basketball", "american-football").forEach(...)` would centralise the routing and a fourth sport would be a one-line change.
- **`PlayerService.fetchFootballStats` has a dead branch** — `catch (NumberFormatException)` returns null with a "shouldn't happen" comment. If it ever does happen, the player is permanently broken until manual DB intervention.
- **`PRODUCT.md`, `README.md`, `CLAUDE.md`** all exist at project root — they should be kept aligned or treated as three sources of truth with explicit precedence.

---

## Priority follow-ups (suggested order)

1. ~~Wire football player photo capture~~ — done (`71e74b7`, QA U1): football headshots now derive from the API-SPORTS media CDN, with a frontend fallback tile.
2. **Add server-side `@Cacheable` on `ApiFootballService.fetchPlayerStats`** (1h TTL) — biggest win for the 100/day budget.
3. **Add `RestClient` timeout config** on `NbaApiService`, `NflApiService`, `ExternalApiService` — prevents a hung external from freezing the scheduler.
4. ~~Test `GlobalExceptionHandler` + `PlayerService.resolvePhotoUrl`~~ — done (Phase 1, `GlobalExceptionHandlerTest` + `PlayerServiceTest`).
5. **Banner on football stats card** explaining the 2024-25 season cap — eliminates silent-incorrect-data risk.
6. ~~Restrict Swagger to `dev` profile~~ — done (`ff9fc60`, QA S2). **Rotate the JWT secret** is the remaining item here — the placeholder in `application.yml` is still committed (Docker/Render deployments override it correctly via `JWT_SECRET`, but the repo default should still be replaced or removed).
7. **Tighten `DataLoader` idempotency** — switch from `count >=` to "all expected competitions present".
8. **Set up Vitest on the frontend** — currently zero test safety net (tracked as HARD-03/Phase 3).

---

## Summary

OneStopSports is a healthy mid-sized side-project. The codebase reads like a teaching project, the layering is clean, and the multi-sport routing pattern is consistent across the three places it lives. The biggest systemic risks are:

1. External API fragility — five dependencies, three undocumented, three rate-limited
2. The API-Football season cap silently serving stale data
3. ~~The unfinished football photo capture~~ — closed (`71e74b7`, QA U1)
4. ~~Test coverage gaps in five out of seven services~~ — closed in Phase 1 of `.planning/` "v1 Harden & Test" (2026-07-08); remaining gap is the frontend (zero Vitest tests, HARD-03/Phase 3)
5. ~~Remaining security tightening (Swagger exposure, rate limiting, JWT expiry/revocation)~~ — closed (QA S1/S2/S3, commits `9a38f89`/`ff9fc60`/`7bcb629`); the JWT placeholder secret in `application.yml` is the one item still open

The rest is well-flagged technical debt of the kind that's natural to accumulate when exploring a domain across multiple sports APIs.
