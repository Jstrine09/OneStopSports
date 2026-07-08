# Requirements: OneStopSports

**Defined:** 2026-07-06 (reverse-engineered from ingested docs — existing deployed app)
**Core Value:** A user never needs to open a separate app per sport — football (soccer), the NBA, and the NFL in one place.

> **Note:** This is an existing, deployed application. The product requirements below (multi-sport, live scores, standings, etc.) are already **shipped and validated in production** — they document the current-state baseline. The **active milestone is Harden & Test**, whose requirements (HARD-01…HARD-04) are the only ones the current roadmap phases build toward. Do not create phases that re-build already-shipped product requirements.

## Milestone 1 (Active): Harden & Test

De-risk the live app. These are the v1 requirements the current roadmap covers.

### Hardening & Test Coverage

- [x] **HARD-01**: Close backend service test-coverage gaps — add unit tests for the currently-untested services: NflApiService, ExternalApiService, ApiFootballService, BallDontLieService, UserService, SportService, PlayerService search + `resolvePhotoUrl` + `toDto`, and GlobalExceptionHandler. Tests must mock external providers (RestClient deep-stubs / package-private test constructors), never call live APIs.
- [ ] **HARD-02**: Add a real-Postgres integration test that verifies the V8 (`name_normalized` backfill + indexes) and V9 (team↔league many-to-many data merge: duplicate-club/player merge, favourite/link re-pointing, `team.league_id` drop) Flyway migrations produce the expected schema and data. These migrations currently run only against real Postgres (Flyway is off in H2 tests) and are unverified by any integration test.
- [ ] **HARD-03**: Establish frontend test coverage — stand up Vitest (currently zero frontend tests) and cover high-value units: the axios client Bearer interceptor, `useLiveScores` STOMP → `setQueryData` behaviour, `getMatchState`/status mapping, and `formatKickoff` timezone handling.
- [ ] **HARD-04**: Fix career-stats 204s for some footballers — resolve the api-sports.io name-match misses (accent / first-name / lastname edge cases in `ApiFootballService.searchPlayerId`) so more football players return real career stats instead of an empty (204) card. Includes a regression test for the previously-missing names.

## Shipped Product Requirements (Validated Baseline)

Already built, deployed, and working in production. Documented for traceability; **not** re-scheduled into the Harden & Test roadmap.

### Product & Features

- ✓ **REQ-multi-sport-consolidation**: Football (soccer) + NBA + NFL in one app so a user never needs a separate app per sport.
- ✓ **REQ-live-scores**: Live scores pushed over WebSocket/STOMP (`/topic/matches/live`); REST polling (60s) is the fallback.
- ✓ **REQ-standings**: League standings per league — NBA grouped by conference, NFL by division, with PCT/GB columns.
- ✓ **REQ-match-detail**: Match detail with event timelines, box scores, and live game clock (stats/lineups stubbed on football free tier).
- ✓ **REQ-team-rosters**: Full team rosters for all three sports.
- ✓ **REQ-player-profiles**: Player profiles with career stats (3 sports), bio (NBA only), and ESPN-CDN headshots (NBA/NFL); football stats single-season capped at 2024.
- ✓ **REQ-favourites**: Authenticated users can add/remove favourite teams and players (JWT-guarded).
- ✓ **REQ-authentication**: Self-hosted username/password registration + login with JWT bearer tokens.
- ✓ **REQ-search**: Accent-insensitive global search across teams + players (min 2 chars); "Dembele" matches "Dembélé".

### Design & Non-Functional

- ✓ **REQ-dark-first-theme**: Purpose-built dark-first theme (light variant present, dark primary).
- ✓ **REQ-live-feels-alive**: Motion on live/match screens reinforces real-time updates (not decorative-only).
- ✓ **REQ-sport-over-chrome**: Crests/scores/stats foregrounded; navigation/decoration recedes; grouped density with hierarchy.
- ✓ **REQ-accessibility-wcag-aa**: WCAG AA contrast; non-data animations honour `prefers-reduced-motion`.
- ✓ **REQ-production-deploy**: Publicly deployed (Vercel + Render + Neon); CORS/WS locked to deploy domains; installable PWA; cold starts mitigated by external UptimeRobot ping.

## Backlog (Future Milestones — NOT in current roadmap)

Captured but deliberately not scheduled into Milestone 1.

### Historical Data

- **HIST-01**: Persist historical match results + player season stats + team head-to-head across all three sports (research complete in `.planning/cowork/HISTORICAL_DATA_RESEARCH.md`; recommended phased path starts with wiring ESPN `/summary` at zero cost). New tables `match_result`, `player_season_stats`, optional `team_h2h_cache`; live data stays ephemeral (Redis-only).

### Notifications

- **NOTF-01**: Push notifications for favourite teams/players (requires FCM/APN/service-worker infra — none exists yet).

## Out of Scope

Explicitly excluded from the Harden & Test milestone.

| Feature | Reason |
|---------|--------|
| Historical-data tracking feature | Deferred to a later milestone; harden the current app before adding surface area |
| Push notifications for favourites | Deferred; adds new infra + surface, not stability |
| Rebuilding shipped features | App is deployed and functional; this milestone tests/hardens existing behaviour |
| Wiring football player headshots | Known gap (`fetchFootballStats` saves external_id but not `player.photo`); nice-to-have, not a hardening blocker for M1 |
| Server-side cache on ApiFootballService / RestClient timeouts / Swagger-dev-only / JWT-secret rotation | Operational/security follow-ups; candidates for a later hardening pass, out of the four chosen M1 scopes |

## Traceability

Milestone 1 (Harden & Test) requirement → phase mapping. Shipped product requirements are baseline (marked Shipped) and not covered by current phases.

| Requirement | Phase | Status |
|-------------|-------|--------|
| HARD-01 | Phase 1 | Complete |
| HARD-02 | Phase 2 | Pending |
| HARD-03 | Phase 3 | Pending |
| HARD-04 | Phase 4 | Pending |
| REQ-multi-sport-consolidation | — (shipped) | Shipped |
| REQ-live-scores | — (shipped) | Shipped |
| REQ-standings | — (shipped) | Shipped |
| REQ-match-detail | — (shipped) | Shipped |
| REQ-team-rosters | — (shipped) | Shipped |
| REQ-player-profiles | — (shipped) | Shipped |
| REQ-favourites | — (shipped) | Shipped |
| REQ-authentication | — (shipped) | Shipped |
| REQ-search | — (shipped) | Shipped |
| REQ-dark-first-theme | — (shipped) | Shipped |
| REQ-live-feels-alive | — (shipped) | Shipped |
| REQ-sport-over-chrome | — (shipped) | Shipped |
| REQ-accessibility-wcag-aa | — (shipped) | Shipped |
| REQ-production-deploy | — (shipped) | Shipped |

**Coverage:**

- Milestone 1 (active) requirements: 4 total (HARD-01…HARD-04)
- Mapped to phases: 4
- Unmapped: 0 ✓
- Shipped product requirements: 14 (baseline, not re-scheduled)

---
*Requirements defined: 2026-07-06 (reverse-engineered bootstrap)*
*Last updated: 2026-07-06 after initial definition*
