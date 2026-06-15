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

**Still open (ranked):** (1) NBA standings render as a flat 1-30 win-sorted ladder — `conference` is never populated for NBA so East/West never group; (2) search is not accent-insensitive ("Dembele"↛"Dembélé") + some star footballers 204 on career-stats from name-match misses; (3) winner not emphasised on finished cards, NBA/NFL league logos null, standings crests null; (4) a11y: form-label associations, `aria-live` on live scores, glass-over-field contrast, some <44px tap targets; (5) duplicate clubs/players across competitions; (6) standings DTO missing PCT/GB server-side.

The detailed concerns below predate this update but remain accurate.

---

## Known incomplete features

### Football player headshots — **gap**
`PlayerService.resolvePhotoUrl` has three layers: persisted `Player.photoUrl` → ESPN CDN URL from `externalId` → `null`. For NBA / NFL, layer 2 fires and works. For football, the design is "lazy capture from API-Football's `player.photo` field during the first stats visit" — but the write is **not yet wired in `fetchFootballStats`**. Only `external_id` is saved. As a result, football players currently show no photo.

Fix: in `PlayerService.fetchFootballStats`, after a successful `fetchPlayerStats(...)` returns, capture the player's photo URL from the API response and call `playerRepository.save(player)` with `player.setPhotoUrl(...)`. Requires plumbing the photo URL out of `ApiFootballService` (currently the DTO mapper discards it).

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

**57 backend tests currently pass.** Coverage is heavily skewed toward auth, `MatchService`, `NbaApiService`, and `LeagueService`.

| Component | Tests | Notes |
|---|---|---|
| `AuthService` | ✅ 6 | |
| `AuthController` | ✅ 7 (`@WebMvcTest`) | Needs `@Import(SecurityConfig.class)` + `excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class` |
| `MatchService` | ✅ 13 | |
| `NbaApiService` | ✅ 12 | Uses `@Mock(answer = Answers.RETURNS_DEEP_STUBS)` for fluent RestClient chains + package-private test constructor |
| `LeagueService` | ✅ 9 | |
| `PlayerService` (career-stats routing) | ✅ 9 (`PlayerServiceCareerStatsTest`) | Covers routing; does NOT cover `resolvePhotoUrl` or `toDto` |
| `OneStopSportsApplicationTests` | ✅ 1 | Context-load smoke; requires `@MockBean RedisConnectionFactory` |
| `NflApiService` | ❌ 0 | 650 LOC, complex 3-level standings parsing — uncovered |
| `ExternalApiService` | ❌ 0 | 456 LOC, biggest single integration point — uncovered |
| `ApiFootballService` | ❌ 0 | Diacritic stripping + multi-strategy name matching — worth covering |
| `BallDontLieService` | ❌ 0 | First-name search + lastname filter has multiple branches |
| `UserService` | ❌ 0 | Favourites CRUD — simple but uncovered |
| `TeamService`, `SportService`, `PlayerService.toDto` | ❌ 0 | Including `resolvePhotoUrl`'s three-layer logic |
| `JwtUtil`, `JwtAuthFilter` | ❌ 0 | Tested transitively via `AuthControllerTest` |
| `GlobalExceptionHandler` | ❌ 0 | The handler-ordering gotcha (ResponseStatusException must come before catch-all) is exactly the kind of regression a focused test would catch |
| `RedisConfig`, `WebSocketConfig` ObjectMapper override | ❌ 0 | The `LocalDateTime` serialisation fix has no regression test |
| Frontend | ❌ 0 | No Vitest setup; TypeScript is the only static check |

**Highest-value tests to write next, ranked:**

1. **`GlobalExceptionHandler`** — small, deterministic, prevents a known regression class (handler-ordering bug)
2. **`PlayerService.resolvePhotoUrl`** — three branches, easy to assert, central to user-visible photo experience
3. **`ApiFootballService.searchPlayerId`** — multiple match strategies + diacritic handling — the most likely silent-failure surface
4. **`MatchService.refreshLiveMatchCache` snapshot-diff detection** — currently uncovered, controls every WebSocket push
5. **`NflApiService` standings parsing** — 3-level nesting (Conference → Division → Group) is brittle

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

## Security concerns (before any public deployment)

These are fine for `localhost` dev but must be tightened before going public:

- **JWT placeholder secret in `application.yml`** looks real (`bXlzdXBlcnNlY3JldGtleWZvcmptYXRjaGRheWFwcGxpY2F0aW9u` = "mysupersecretkeyformjmatchdayapplication"). Anyone with the public repo can forge JWTs against any local deployment that didn't override it via `application-local.yml`. Docker deployments correctly use `JWT_SECRET` env var.
- **CORS fully open:** `setAllowedOriginPatterns(List.of("*"))` with `setAllowCredentials(true)`. With credentials + wildcard, a malicious site loaded in a logged-in user's browser can call our API with their JWT.
- **Swagger UI publicly accessible.** Intentional for dev; in production it's a self-service inventory of every endpoint + auth requirements — useful to attackers.
- **No rate limiting on `/api/auth/login`.** Brute-force friendly. No account lockout.
- **No request-size limits, no CAPTCHA on register, no email verification.**
- **WebSocket `/ws/**` is `permitAll`** with no token verification. Anyone can subscribe to `/topic/matches/live`. Fine today (data is public) — but if user-specific topics are added (`/user/{id}/notifications`), the model needs revisiting.
- **24h JWT expiry, no refresh token, no revocation.** Stolen tokens are valid until expiry. Acceptable for a side-project.

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

1. **Wire football player photo capture** in `PlayerService.fetchFootballStats` — small change, finishes a half-shipped feature.
2. **Add server-side `@Cacheable` on `ApiFootballService.fetchPlayerStats`** (1h TTL) — biggest win for the 100/day budget.
3. **Add `RestClient` timeout config** on `NbaApiService`, `NflApiService`, `ExternalApiService` — prevents a hung external from freezing the scheduler.
4. **Test `GlobalExceptionHandler` + `PlayerService.resolvePhotoUrl`** — small, high-value coverage gaps.
5. **Banner on football stats card** explaining the 2024-25 season cap — eliminates silent-incorrect-data risk.
6. **Restrict CORS + Swagger to `dev` profile** — required before any public deployment.
7. **Tighten `DataLoader` idempotency** — switch from `count >=` to "all expected competitions present".
8. **Set up Vitest on the frontend** — currently zero test safety net.

---

## Summary

OneStopSports is a healthy mid-sized side-project. The codebase reads like a teaching project, the layering is clean, and the multi-sport routing pattern is consistent across the three places it lives. The biggest systemic risks are:

1. External API fragility — five dependencies, three undocumented, three rate-limited
2. The API-Football season cap silently serving stale data
3. The unfinished football photo capture
4. Test coverage gaps in five out of seven services
5. Security tightening required before any public deployment

The rest is well-flagged technical debt of the kind that's natural to accumulate when exploring a domain across multiple sports APIs.
