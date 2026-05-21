# Codebase Concerns

**Analysis Date:** 2026-05-21

A candid catalog of risks, debt, gaps, and fragility in OneStopSports. The app works and demos well, but it leans heavily on free-tier external APIs, several of which are undocumented, rate-limited, or season-capped. Many of the items below are documented in code comments — this file consolidates them in one place and adds concerns the comments don't acknowledge.

---

## External API Risks

The app is a thin orchestration layer over **five** external APIs. None are fully under our control; three are free-tier; three are undocumented/unofficial.

### football-data.org (`ExternalApiService`)

- **Rate limit: 10 requests/minute on free tier.** `DataLoader` already sleeps 6.2s between competitions (`src/main/java/com/onestopsports/config/DataLoader.java:167,208`). Live score scheduler ticks every 30s and calls `fetchLiveMatchDtos()` once per tick — within budget, but any user-triggered date browse on the LeaguesPage burns an extra request.
- **Match stats + lineups not available on free tier.** Endpoints exist on our API surface but return `Map.of()`. See *Known incomplete features* below.
- API key lives in `application-local.yml` (gitignored) and `application.yml` carries the placeholder `YOUR_API_KEY_HERE`.

### API-Football / api-sports.io (`ApiFootballService`)

- **Hard daily cap: 100 requests/day on free tier.** Used for football player career stats. Every uncached page view consumes the budget — see *Operational concerns* for the missing server-side cache.
- **Season cap: free plan only serves up to season 2024.** Hard-coded `FREE_TIER_MAX_SEASON = 2024` (`ApiFootballService.java:92`). As of mid-2026 we serve the 2024-25 season label instead of the in-progress 2025-26 season — **silently incorrect data** for any user expecting current-season stats.
- **League search filter mandatory on free tier.** We map our football-data.org competition IDs to API-SPORTS league IDs via a hand-maintained `Map.of()` of six entries (`ApiFootballService.java:52-59`). Any competition outside this map (domestic cups, lower divisions) returns no stats with no user-facing explanation.
- **Diacritic stripping is implemented but the rest of the name-matching pipeline is still ASCII-naive.** `stripAccents()` (`ApiFootballService.java:290`) is applied to the *search* term, but the loose fallback match at line 171-178 compares against the original accented lastname from the DB. If the DB stores "Dembélé" and API-SPORTS returns "Dembele", the lowercase string match fails. The exact-match branch above does compare full names case-insensitively but again against accented DB values. **Verdict: partial fix — works for exact-match cases, can still miss diacritic players in the fallback path.** Worth a follow-up pass to normalise both sides before comparison.
- **Stats only cover one season at a time on free tier.** The DTO's `careerRow` is always `null` for football — flagged in code, but means UI parity with NBA/NFL career stats is impossible.
- Mid-season transfers produce multiple rows per player-season; only `competition name` disambiguates them in the UI.

### balldontlie.io (`BallDontLieService`)

- **Rate limit: 5 requests/minute on free tier.** Called lazily from `getPlayerBioById()` — usually fine, but a power-user clicking through the NBA roster fast can trip the limit silently (failures return `Optional.empty()` and the bio card hides).
- **First-name-only search.** The API ignores the lastname; we filter results in code (`BallDontLieService.java:78-81`). For common first names ("Chris", "James") this means up to 10 results filtered down to one — works, but fragile if the API changes pagination behaviour.
- **Hyphenated lastnames stored as one token** ("Gilgeous-Alexander"). Works today but means `equalsIgnoreCase` comparison is exact — any tokenisation difference between our DB and balldontlie breaks the match.
- **Free tier excludes stats endpoints.** Only `/players` and `/teams` are usable. Documented in code comments.

### ESPN unofficial APIs (NBA + NFL — `NbaApiService`, `NflApiService`, `NflDataLoader`, `NbaDataLoader`)

- **Undocumented and unofficial.** No SLA, no changelog, no rate limit documentation. Any silent field rename or path change breaks the app — `@JsonIgnoreProperties(ignoreUnknown = true)` (`NflApiService.java:40-41`) protects against added fields only, not renames or removals.
- **Three different ESPN subdomains in use per sport:**
  - `site.api.espn.com/apis/site/v2/...` — teams, rosters, scoreboard
  - `site.web.api.espn.com/apis/v2/...` (NBA) and `site.api.espn.com/apis/v2/...` (NFL) — standings
  - `site.web.api.espn.com/apis/common/v3/...` — career stats
  
  NBA uses `site.web.api` subdomain for standings; NFL uses `site.api`. **Easy footgun** if someone copies the NBA pattern when adding a new sport. The differences are noted in `application.yml:33-46` comments.
- **No retry / circuit breaker.** If ESPN returns 500, we log a warning and return an empty list. The next 30-second scheduler tick will retry blindly.
- **Hardcoded division map.** `NflApiService.DIVISION_BY_ABBR` (`NflApiService.java:58-83`) lists all 32 abbreviations. If an NFL team relocates and changes its abbreviation (e.g. OAK → LV in 2020) the team falls into `"Unknown Division"` until someone updates the map.
- **Roster shape differs per sport.** NBA returns a flat `athletes` array; NFL groups by `offense`/`defense`/`specialTeam`. Adding a third ESPN-backed sport means a third roster-parsing strategy.
- **ET timezone conversion lives in two services.** Both `NbaApiService` and `NflApiService` independently call `OffsetDateTime.parse(...).atZoneSameInstant(ZoneId.of("America/New_York")).toLocalDateTime()`. Duplication; one easy place for the timezone to drift between sports.

### Cross-cutting

- **Five APIs × no central rate-limit awareness.** Each service does its own retry-on-failure-by-returning-empty. No coordinated backoff or budget tracking.
- **No request observability.** Beyond `log.warn(...)` no metrics are emitted (no API call counter, no latency histogram). A noisy production debugging story.

---

## Data Model Concerns

- **`Player.externalId` is soft-typed.** `VARCHAR(64)` storing three different ID semantics depending on the sport (`Player.java:39-45`):
  - basketball / american-football → ESPN athlete ID (numeric string, set at seed time)
  - football → API-Football player ID (numeric string, set lazily on first stats visit)
  
  A future maintainer parsing this field has no compile-time signal of which API it belongs to — they have to walk `player → team → league → sport.slug`. Worth a `provider` enum or per-sport columns if the matrix grows.
- **`Player.photoUrl` resolution is split between persistence and derivation.** `PlayerService.resolvePhotoUrl` (`PlayerService.java:202-225`) has three layers:
  1. Persisted column (football, populated post-first-stats-visit — though the actual write was deferred and may not be in `fetchFootballStats` yet)
  2. ESPN deterministic CDN URL constructed from `externalId` (NBA/NFL)
  3. `null` fallback
  
  Football players never visited have no photo. NBA/NFL photos require the CDN URL pattern to stay stable (it has for years, but it's still external state we don't control). Code comments acknowledge layer 1 is a "follow-up" — read carefully, `fetchFootballStats` does not currently write `photoUrl`, only `externalId`. **Football player photos will never populate until that write is added.**
- **Lazy externalId backfill can fail silently.** If `searchPlayerId` misses (rate-limited, name typo, non-mapped league, no league filter match), the column stays null and the stats card is hidden forever for that player with no re-try mechanism. Manual DB edit required to recover. See *Style/maintainability* — diacritic stripping is partial.
- **`UserAccount` not `User`** — correct workaround (`user` is reserved in Postgres) but worth flagging because the JPA entity name vs. table name vs. controller path triad (`/api/users/...`) is mildly confusing.
- **`ON DELETE CASCADE` on `favorite_player(player_id)` and `favorite_team(team_id)`.** Means any roster re-seed (NBA/NFL data loaders explicitly delete pre-V6 stale rows in `NbaDataLoader.java:204-208`) **silently wipes users' favourites**. Documented in code comments but no user-facing notification, no undo. Acceptable on a personal-project demo; unacceptable in production.
- **No `external_id` uniqueness constraint on `player`.** Two seed runs that misfire could theoretically duplicate rows. Defended in practice by the "skip if seeded" guards in the data loaders, but no DB-level safety net.
- **No optimistic locking / `@Version`** on any entity. Concurrent writes to the same favourite row could race. Low-impact for a demo app.
- **`spring.jpa.open-in-view` defaults to true** (not explicitly set in `application.yml`). `PlayerService.toDto` relies on lazy loading the team/league/sport chain outside any explicit `@Transactional` boundary (`PlayerService.java:182`). It works because OSIV keeps the Hibernate session open for the duration of the web request — but this is a known anti-pattern that leaks DB access into the view layer and can cause N+1 query storms. Worth being aware of even if not fixing now.

---

## Security Concerns

- **`application.yml` contains placeholder secrets**, all clearly named (`YOUR_API_KEY_HERE`, `YOUR_BALLDONTLIE_KEY_HERE`, `YOUR_API_FOOTBALL_KEY_HERE`). The Base64 JWT secret in `application.yml:65` is **a real-looking value** (`bXlzdXBlcnNlY3JldGtleWZvcmptYXRjaGRheWFwcGxpY2F0aW9u` = "mysupersecretkeyformjmatchdayapplication"). It's a placeholder dev key, not random, and **anyone with the public repo can forge JWTs against any local-dev deployment** that didn't override it via `application-local.yml`. Production deployments via Docker correctly read `JWT_SECRET` from env (`application-docker.yml:29`), but the in-code placeholder is dangerously plausible.
- **`application-local.yml` is correctly gitignored** (verified — `.gitignore:29` and `git check-ignore` confirms). Not in git history either.
- **`.env` is gitignored**. Good.
- **CORS is fully open in dev:** `setAllowedOriginPatterns(List.of("*"))` with `setAllowCredentials(true)` (`SecurityConfig.java:78-81`). With `allowCredentials=true` and a wildcard origin pattern, **a malicious site loaded in a logged-in user's browser can call the API with their cookie/JWT.** Fine for `localhost` development; **must be tightened before any public deployment.**
- **All GETs are public.** `requestMatchers(HttpMethod.GET, "/api/**").permitAll()` (`SecurityConfig.java:48`). Includes `/api/users/me/favorites/teams` — but that's overridden by the `/api/users/me/**` matcher one line lower; ordering matters. Worth a comment-test to make sure no GET endpoint sneaks in below this rule and accidentally becomes public.
- **Swagger UI and `/v3/api-docs` are publicly accessible.** Documented in code as intentional for dev (`SecurityConfig.java:51-53`). **In production this is a self-service inventory of every endpoint + their auth requirements** — useful to attackers. Should be `permitAll` only in dev profile.
- **JWT expiry is 24 hours** (`application.yml:66`), no refresh token, no revocation. Stolen tokens are valid until expiry. Acceptable for a side-project; documented for awareness.
- **WebSocket endpoint `/ws/**` is permitAll** with no token verification (`SecurityConfig.java:50`). Anyone can subscribe to `/topic/matches/live`. Probably fine since the data is public anyway, but if user-specific topics are ever added (e.g. `/user/{id}/notifications`) the auth model needs revisiting.
- **No rate limiting on `/api/auth/login`.** Brute-force friendly. No account lockout.
- **No request-size limits, no CAPTCHA on register**, no email verification.
- **`show-sql` is off in `application.yml`** — good, no SQL leakage to logs. But `format_sql: true` will still emit if `show-sql` is flipped.
- **`spring.jpa.hibernate.ddl-auto: validate`** — good, no destructive auto-migrations in prod.

---

## Frontend Concerns

- **No global error boundary visible.** A throw in any page component would unmount the app. Worth a top-level `<ErrorBoundary>`.
- **React Query is the only caching layer between the user and the backend.** 24h `staleTime` on player bio/stats (`PlayerDetailPage.tsx:66,78`) is correct for the API budget, but **the cache is per-tab**. Cross-user requests share nothing — see *Operational concerns*.
- **No `.env.example` for the frontend** — Vite picks up `VITE_*` vars but there are no documented frontend env vars. Adequate today; brittle if anything ever needs configuring.
- **Vite proxy is dev-only.** Production deployment requires the React app to be served from the same origin (or CORS to allow it). No production deployment of the frontend exists yet, so this is theoretical.
- **WebSocket URL derived from `window.location`.** Works behind a reverse proxy that forwards `/ws`; subtly breaks if anyone deploys behind a path prefix (e.g. `/onestopsports/ws`).
- **WebSocket reconnect at 5s** with no exponential backoff. If the backend is in a restart loop the client hammers it every 5s.
- **Initials fallback for missing player photos** (mentioned in comments) — implementation detail, but works only if the component renders DOB or name in fallback. Worth manual verification once stats writes are added.
- **Standings table treats `division !== null` as the NFL marker** (`StandingsTable.tsx:88`). If we ever add another sport with a division field (CFL, AFL, college sports) the layout will mis-fire.
- **No tests on any frontend code.** Vitest is not configured. The only safety net is TypeScript's compile-time checks.
- **`MatchCard.tsx` and `MatchDetailPage.tsx` both implement `formatKickoff`** (per CLAUDE.md). Verify they stay in sync or extract to a shared util.

---

## Testing Gaps

**48 tests currently pass.** Coverage is heavily skewed toward auth and a couple of services.

| Component | Tests? | Notes |
|-----------|--------|-------|
| `AuthService` | ✅ 6 tests | |
| `AuthController` | ✅ 7 `@WebMvcTest` tests | |
| `MatchService` | ✅ 13 tests | |
| `NbaApiService` | ✅ 12 tests | |
| `LeagueService` | ✅ 9 tests | |
| `PlayerService` (career stats branch) | ✅ partial — `PlayerServiceCareerStatsTest` exists | covers routing only, not `resolvePhotoUrl` or `toDto` |
| `NflApiService` | ❌ none | 650 LOC, multi-level standings parsing, no coverage |
| `ExternalApiService` | ❌ none | 456 LOC, biggest single integration point — uncovered |
| `ApiFootballService` | ❌ none | Includes the partial diacritic-stripping logic |
| `BallDontLieService` | ❌ none | First-name-search-with-lastname-filter logic has multiple branches and a fallback — easy to break |
| `UserService` | ❌ none | Favourites CRUD — relatively simple, still uncovered |
| `TeamService`, `SportService`, `PlayerService.toDto` | ❌ none | Including `resolvePhotoUrl`'s three-layer logic |
| `JwtUtil`, `JwtAuthFilter` | ❌ none | Tested transitively via AuthControllerTest but no direct tests |
| `GlobalExceptionHandler` | ❌ none | The handler-ordering gotcha (ResponseStatusException must come before catch-all) is exactly the kind of regression a focused test would catch |
| `RedisConfig`, `WebSocketConfig` ObjectMapper override | ❌ none | The fix for `LocalDateTime` serialisation has no regression test |
| Frontend | ❌ none | No Vitest setup; TypeScript is the only static check |

**Highest-value missing tests, ranked:**

1. `GlobalExceptionHandler` — small, deterministic, prevents a known regression.
2. `PlayerService.resolvePhotoUrl` — three branches, easy to assert on, central to the user-visible photo experience.
3. `ApiFootballService.searchPlayerId` — multiple match strategies + diacritic handling, currently the most likely silent-failure surface.
4. `MatchService.refreshLiveMatchCache` snapshot-change detection — currently uncovered, controls every WebSocket push.

---

## Known Incomplete Features

The CLAUDE.md "Stubbed" and "Remaining Tasks" sections name these explicitly:

- **`MatchService.getMatchStats()` returns `Map.of()`** (`MatchService.java:140`). Free tier limitation. Endpoint exists, controller exists, UI renders "coming soon".
- **`MatchService.getMatchLineups()` returns `Map.of()`** (`MatchService.java:146`). Same story.
- **Football player photos** — `Player.photoUrl` is intended to be populated from API-Football's `player.photo` field on the first stats visit. **The write is not actually wired up in `fetchFootballStats`** — only `externalId` is saved (`PlayerService.java:154`). The code comment at line 188-189 says this is "added in a follow-up" but the follow-up never landed. Footballers will never display a photo unless this is finished.
- **Diacritic-stripping fix is half-done** in `ApiFootballService` — `Normalizer` is imported (line 12) and `stripAccents()` exists (line 290), but the fallback name-match at lines 171-178 still compares against accented strings. Either complete it (normalise both sides) or remove the import and document the limitation.
- **NFL standings frontend** — CLAUDE.md flags this as a remaining task, but `StandingsTable.tsx:88-179` does in fact have a conference→division grouped layout that renders when any entry has a non-null `division` field. **CLAUDE.md is outdated**; this item is done and should be removed from the Remaining Tasks list.
- **No NFL career stats UI test path** — backend `fetchCareerStats` exists; visual verification of the rendered table for an off-season player would be valuable.
- **Push notifications for favourite teams** — listed as nice-to-have. No infrastructure (no FCM/APN integration, no service worker on the frontend).

---

## Operational Concerns

### Caching

- **No server-side cache on `ApiFootballService`.** Every uncached request burns one of 100 daily calls. Code comments acknowledge this (`ApiFootballService.java:34-39`). The current mitigation is *(a)* persisting the externalId so subsequent stats requests skip the search step, and *(b)* React Query's 24h `staleTime` on the frontend. **But React Query's cache is per-tab, per-user.** Two different users hitting the same player burn two API calls. A `@Cacheable("player-stats-football")` annotation with a 1-hour TTL would dramatically improve the budget.
- **No server-side cache on `BallDontLieService`** either — same problem on a smaller scale (5 req/min instead of 100/day).
- **No cache on `fetchStandings()` calls** — they hit ESPN every page-load. Acceptable today; worth a 5-minute TTL once traffic grows.
- **`@Cacheable("matches")` 30s TTL** on live matches is correct, but key is `SimpleKey.EMPTY` because the method has no args. **Anyone adding a parameter to `getLiveMatches()` will silently break the cache-key contract** used in the WebSocket push path (`MatchService.java:187`).
- **No cache eviction on logout / favourite toggle.** Frontend invalidates manually; backend `@Cacheable` data could go stale by up to 30s.

### Scheduled jobs

- **`refreshLiveMatchCache` runs every 30s** with `@Scheduled(fixedDelay = 30_000)`. The whole body is wrapped in a single `try/catch (Exception)` (`MatchService.java:160-201`), so a transient failure is logged and the next tick runs as normal. **But:**
  - It calls **three** external APIs sequentially each tick (football live + NBA leagues × NFL leagues). If any one hangs (no per-call timeout configured on RestClient), the entire scheduler thread blocks until the default Java HTTP timeout fires. Other scheduled jobs on the same `TaskScheduler` would wait too. Adding `.requestFactory()` configuration with explicit connect+read timeouts is a one-liner that would harden this significantly.
  - `@Scheduled` uses Spring's default single-threaded scheduler pool. If a tick takes >30s, the next tick is delayed (not skipped — `fixedDelay` measures from the *end* of the previous run). Probably fine; just documented for awareness.
  - On exception the snapshot map is not rolled back, but since the snapshot is only updated *after* successful broadcast (`MatchService.java:179`), this is naturally consistent.

### Data loaders

- **All three data loaders swallow exceptions** and let the app start with partial data (`DataLoader.java:81-85`, `NbaDataLoader.java:107-111`, similar in `NflDataLoader`). On a transient network blip during seeding, the app may serve an empty Premier League with no obvious sign anything's wrong. The user has to re-run.
- **No retry between competitions** inside `DataLoader.seed()`. A single 5xx from football-data.org during the PL fetch loses the entire league plus all its players.
- **Idempotency check uses count**: `leagueRepository.count() >= COMPETITION_IDS.length`. If you ever add a 7th competition, the loader will *think* it's already seeded if 6 leagues exist from a prior run, and silently skip the new one. The NBA / NFL loaders use the more robust "all-30-teams + all-have-externalIds" check.
- **Data loaders run in startup order Spring decides.** No explicit ordering between `DataLoader`, `NbaDataLoader`, `NflDataLoader`. Today they don't depend on each other (each owns its own Sport row), but the order is implementation-defined.

### Error handling

- **`GlobalExceptionHandler` is well-thought-out** but **the catch-all `@ExceptionHandler(Exception.class)` returns 500 with the exception message exposed in the response body.** Internal error details leak to the client (e.g. JDBC exception messages with column names). Acceptable in dev; should be redacted in prod.
- **No structured logging.** Every `log.warn` uses string interpolation. A future ELK/Datadog setup will struggle to filter or aggregate.

### Database

- **Flyway runs at startup, `ddl-auto: validate`** — correct production-style config.
- **No connection-pool tuning** — Spring Boot defaults (HikariCP, 10 connections). Probably fine; just unparameterised.
- **No database backups documented.** A `docker-compose down -v` wipes everything.

### Docker

- **`docker-compose.yml` relies on `.env` at project root.** If a developer forgets to copy `.env.example` to `.env`, the app boots with `${JWT_SECRET}` unresolved and authentication breaks in a confusing way. Worth a healthcheck or startup assertion that mandatory env vars are non-empty.

---

## Style / Maintainability

- **Comments are excellent.** The codebase reads like a teaching project, with detailed inline explanations matching the user's stated style preference (junior-developer-friendly). This is a strength worth preserving.
- **`ApiFootballService.java:12` imports `java.text.Normalizer`** for the `stripAccents` helper. The helper exists; it's called in `searchPlayerId` (lines 132-133). The "mid-flight unfinished" framing in the meta-prompt is **slightly outdated** — diacritic stripping *is* in use for the search term. **But:** the loose lastname fallback at lines 171-178 still compares against the accented DB value (`parts[parts.length - 1].toLowerCase()`). For a player like "Vinícius" the search succeeds (sends "Vinicius") but the fallback match would compare API result "Vinicius" against DB "vinícius" and fail. Worth completing the fix by normalising both sides.
- **Service class size is creeping up.** `NflApiService` is 650 LOC, `NbaApiService` 587, `ExternalApiService` 456. All are doing fetching + DTO mapping + standings/scoreboard parsing in one class. The shape mirrors the upstream API; not unreasonable, but a maintainer onboarding to NFL has to load all 650 lines into their head.
- **Duplicated patterns across NBA/NFL services**: ET timezone conversion (`OffsetDateTime.parse(...).atZoneSameInstant(...)`), the three-RestClient-instances-per-service shape, ESPN status code → our status enum mapping. A small shared `EspnTimezoneUtil` or `EspnStatusMapper` would DRY this up.
- **Inner records named `EspnXxx` collide across services.** Both `NbaApiService` and `NflApiService` declare `EspnTeamsResponse`, `EspnSport`, `EspnLeague`, etc. They're package-private inside each service, so no compile clash — but readers grep'ing for `EspnAthlete` get hits in two files with slightly different shapes (NFL `EspnAthlete` lives inside an `EspnPositionGroup.items`; NBA is flat). Documented in CLAUDE.md.
- **`MatchService.fetchNonFootballLiveMatches`** uses `leagueRepository.findBySport_Slug(...)` twice (once for basketball, once for american-football). A `List.of("basketball", "american-football").forEach(...)` would centralise the routing, and adding a fourth sport would be a one-line change.
- **`PlayerService.fetchFootballStats` has a subtle dead branch** — the `catch (NumberFormatException)` (line 134-138) returns null without re-trying, but the comment says "shouldn't happen". If it ever does happen, the player is permanently broken until manual DB intervention.
- **No `@Slf4j` (Lombok) anywhere** — every service declares `private static final Logger log = LoggerFactory.getLogger(...)`. Minor; consistent.
- **`CLAUDE.md` is partially out of date.** Specifically: "NFL standings frontend currently renders as flat table — needs conference/division grouping" is **no longer true** (see `StandingsTable.tsx:91-179`). Worth a doc sweep.
- **`PRODUCT.md` and `README.md` exist** at project root — verify they say the same thing as CLAUDE.md, or risk drift across three sources of truth.
- **No CI configuration visible** (`.github/workflows`, `Jenkinsfile`, etc.). `mvn test` must be run manually; nothing enforces it on PR.
- **`OneStopSports_Study_Guide.pdf` and `study_guide_source.html` are at the project root.** The PDF is in `.gitignore` (line 35) — good. The HTML is generated and gitignored too. Both are personal-study artefacts, not project code.

---

## Summary

The codebase is a healthy mid-sized side-project. The biggest **systemic risks** are:

1. **External API fragility** — 5 dependencies, 3 of which are undocumented or strictly free-tier. No central rate-limit accounting, no circuit breaker, no per-request timeout.
2. **API-Football season cap is silently wrong** — we serve 2024-25 data with no UI banner explaining why. Highest-priority *user-visible* issue.
3. **Football player photo write is unfinished** — explicit gap from a half-completed change.
4. **Coverage gaps in five out of seven services** — most importantly `ExternalApiService` and `ApiFootballService`.
5. **Security tightening required before any public deployment** — open CORS with credentials, public Swagger, JWT placeholder secret that looks real.

The rest is well-flagged technical debt of the kind that's natural to accumulate while exploring a domain across multiple sports APIs.

---

*Concerns audit: 2026-05-21*
