# Roadmap: OneStopSports

## Overview

OneStopSports is an existing, publicly-deployed multi-sport app (football + NBA + NFL). All product capabilities — live scores, standings, match detail, rosters, player profiles, favourites, auth, search — are already shipped and working (see the "Shipped Baseline" section below). This roadmap's active milestone is **Harden & Test**: de-risking the live app by closing its known test-coverage gaps and fixing one known functional gap, without rebuilding any working feature. The journey: prove the untested backend services behave (Phase 1) → verify the two Postgres-only Flyway migrations against real Postgres (Phase 2) → establish frontend testing from zero (Phase 3) → fix the football career-stats name-match misses with a regression guard (Phase 4).

## Milestones

- ✅ **Shipped Baseline** — the deployed app (all 14 product requirements) — SHIPPED (pre-bootstrap)
- 🚧 **v1 Harden & Test** — Phases 1–4 (active)
- 📋 **Backlog** — Historical data, push notifications (not scheduled)

## Phases

**Phase Numbering:**

- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

- [ ] **Phase 1: Backend Service Test Coverage** - Unit-test the currently-untested backend services with mocked providers
- [ ] **Phase 2: Postgres Migration Integration Tests** - Verify the V8 + V9 Flyway migrations against real Postgres
- [ ] **Phase 3: Frontend Test Foundation** - Stand up Vitest and cover high-value frontend units
- [ ] **Phase 4: Career-Stats Name-Match Hardening** - Fix football career-stats 204s from api-sports.io name-match misses

## Phase Details

### Phase 1: Backend Service Test Coverage

**Goal**: The backend services that currently have zero tests are covered by unit tests that mock their external providers, so their routing, mapping, and soft-fail behaviour is verified and regressions are caught by `mvn test`.
**Depends on**: Nothing (first phase of the milestone; builds on the existing 66-test baseline)
**Requirements**: HARD-01
**Success Criteria** (what must be TRUE):

  1. NflApiService, ExternalApiService, ApiFootballService, and BallDontLieService each have unit tests covering their happy-path mapping AND their swallow-RestClientException soft-fail (empty list / null / Optional.empty), with no live network calls.
  2. UserService, SportService, and GlobalExceptionHandler have unit tests (GlobalExceptionHandler asserts the documented status mappings, including `ResponseStatusException` passthrough before the catch-all).
  3. PlayerService `resolvePhotoUrl` (persisted → ESPN-CDN-derived → null), `toDto`, and search are unit-tested.
  4. The full suite still passes (`mvn test`), the pre-existing 66 tests are not regressed, and new tests mock providers via RestClient deep-stubs / package-private test constructors following the existing NbaApiServiceTest pattern.

**Plans**: 4/5 plans executed
**Wave 1**

- [x] 01-01-PLAN.md — Add package-private test constructors to NflApiService/ExternalApiService/ApiFootballService/BallDontLieService (+ widen BallDontLie records) [Wave 1]
- [x] 01-04-PLAN.md — UserServiceTest + SportServiceTest (CRUD guards + mapping) [Wave 1]
- [x] 01-05-PLAN.md — PlayerServiceTest (photo/toDto/search) + GlobalExceptionHandlerTest (dispatch order) [Wave 1]

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 01-02-PLAN.md — NflApiServiceTest + ExternalApiServiceTest (mapping + soft-fail) [Wave 2]
- [ ] 01-03-PLAN.md — ApiFootballServiceTest + BallDontLieServiceTest (mapping + soft-fail) [Wave 2]

**Cross-cutting constraints:**

- Neither test makes a live network call — the RestClient chain is fully mocked with RETURNS_DEEP_STUBS.
- The full suite (baseline 66 + these new tests) is green via mvn test.

### Phase 2: Postgres Migration Integration Tests

**Goal**: The two Flyway migrations that only ever run against real Postgres (V8 `name_normalized`, V9 team↔league M:N data merge) are verified by an integration test against a real Postgres instance, so their schema changes and one-time data merges are proven correct rather than only compile-checked.
**Depends on**: Phase 1
**Requirements**: HARD-02
**Success Criteria** (what must be TRUE):

  1. An integration test runs the full V1→V9 Flyway migration chain against a real Postgres instance (e.g. Testcontainers or an equivalent real-Postgres harness), separate from the H2 unit-test profile.
  2. The test asserts V8 outcomes: `team.name_normalized` and `player.name_normalized` columns exist, are indexed, and are backfilled accent-stripped/lower-cased for seeded rows.
  3. The test asserts V9 outcomes: duplicate clubs sharing `(sport_id, external_id)` are merged into one canonical row, the `team_league` join table is populated, players/league-links/favourites are re-pointed onto the canonical rows, and `team.league_id` is dropped.
  4. The migration integration test runs in `mvn test` (or a clearly-documented `mvn` profile) and is green, without breaking the existing H2-based suite.

**Plans**: TBD

### Phase 3: Frontend Test Foundation

**Goal**: The frontend has a working test runner and its highest-value units are covered, so the currently-untested React app can catch regressions in auth wiring, live-score push, and time/status formatting.
**Depends on**: Phase 2
**Requirements**: HARD-03
**Success Criteria** (what must be TRUE):

  1. Vitest is installed and configured for the Vite + React + TS frontend, and `npm test` runs the suite green.
  2. The axios client's request interceptor is tested to attach `Authorization: Bearer <jwt>` from localStorage when present and omit it when absent.
  3. The `useLiveScores` STOMP hook is tested to push received messages into React Query via `setQueryData(['matches','live'], ...)`, and `formatKickoff` is tested to append "ET" only when `timezone === 'ET'`.
  4. Match status mapping (`getMatchState` / live-vs-finished) is tested for football and NBA/NFL status strings (including ESPN's native `"LIVE"`).

**Plans**: TBD
**UI hint**: yes

### Phase 4: Career-Stats Name-Match Hardening

**Goal**: More football players return real career stats instead of an empty (204) card, because `ApiFootballService.searchPlayerId` matches names it previously missed — verified by a regression test over the previously-failing names.
**Depends on**: Phase 3
**Requirements**: HARD-04
**Success Criteria** (what must be TRUE):

  1. Football players who previously returned a 204 career-stats card because of an api-sports.io name-match miss now resolve to a player ID and return stats (accent / first-name / lastname edge cases handled on both sides of the comparison).
  2. A unit test over the specific previously-missing names asserts they now match, and a name that genuinely has no data still soft-fails cleanly (204, no error) rather than mismatching to the wrong player.
  3. The fix preserves the free-tier constraints (still respects `FREE_TIER_MAX_SEASON = 2024`, still persists the resolved id to `Player.external_id`, no extra per-request API calls beyond the existing two-step search→fetch flow).
  4. The full backend suite (`mvn test`) remains green.

**Plans**: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Backend Service Test Coverage | 4/5 | In Progress|  |
| 2. Postgres Migration Integration Tests | 0/TBD | Not started | - |
| 3. Frontend Test Foundation | 0/TBD | Not started | - |
| 4. Career-Stats Name-Match Hardening | 0/TBD | Not started | - |

## Shipped Baseline (Not Re-scheduled)

These product capabilities are already built and deployed. They are recorded so the roadmap is honest about current state — **no phase rebuilds them.** See REQUIREMENTS.md "Shipped Product Requirements" and `.planning/cowork/` for detail.

- Multi-sport consolidation (football + NBA + NFL) · live scores (WebSocket push) · standings (NBA conference / NFL division) · match detail + box scores + timelines · team rosters · player profiles (career stats / bio / headshots) · favourites · JWT auth · accent-insensitive search · dark-first theme · WCAG-AA accessibility baseline · public deploy (Vercel + Render + Neon) as an installable PWA.
