---
gsd_state_version: '1.0'  # placeholder; syncStateFrontmatter overwrites on first state.* call
status: planning
progress:
  total_phases: 4
  completed_phases: 0
  total_plans: 0
  completed_plans: 0
  percent: 0
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-07-06)

**Core value:** A user never needs to open a separate app per sport — football (soccer), NBA, and NFL in one place.
**Current focus:** Phase 1 — Backend Service Test Coverage (milestone: v1 Harden & Test)

## Current Position

Phase: 1 of 4 (Backend Service Test Coverage)
Plan: 0 of TBD in current phase
Status: Ready to plan
Last activity: 2026-07-06 — Reverse-engineered GSD scaffolding from ingested docs (existing deployed app; Milestone 1 = Harden & Test)

Progress: [░░░░░░░░░░] 0%

## Performance Metrics

**Velocity:**
- Total plans completed: 0
- Average duration: — min
- Total execution time: 0.0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| - | - | - | - |

**Recent Trend:**
- Last 5 plans: —
- Trend: —

*Updated after each plan completion*

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table (23 ADR decisions recorded from `.planning/cowork/DECISIONS.md`, precedence 0). Most relevant to the Harden & Test milestone:

- Tests must MOCK external providers (RestClient deep-stubs / package-private test constructors) — never call live APIs (free-tier limits are real). Follow the existing NbaApiServiceTest pattern.
- Flyway is OFF in H2 tests → V8/V9 need a real-Postgres integration harness (this is exactly HARD-02).
- GlobalExceptionHandler: `ResponseStatusException` handler MUST precede the `Exception` catch-all (assert this in tests).
- Mandatory junior-developer inline comments on every new Java file (teaching project — hard rule, not style).

### Pending Todos

None yet.

### Blockers/Concerns

- **[Milestone] Existing app, do not regress:** 66 backend tests across 9 classes are the green baseline — new work must keep them passing.
- **[Milestone] Three sources of truth:** `PRODUCT.md`, `README.md`, and `CLAUDE.md` at the project root should be kept aligned with `.planning/` as Harden & Test lands changes.
- **[HARD-04] Silent 204s:** api-sports.io name-match misses currently fail closed with no retry — the fix must not mismatch to the wrong player.

## Deferred Items

Items acknowledged and carried forward (backlog — not scheduled into Milestone 1):

| Category | Item | Status | Deferred At |
|----------|------|--------|-------------|
| Feature | Historical-data tracking (match_result / player_season_stats / h2h) | Backlog | 2026-07-06 bootstrap |
| Feature | Push notifications for favourites (FCM/APN/service worker) | Backlog | 2026-07-06 bootstrap |
| Ops/Security | Football player photo wiring, ApiFootball server-side cache, RestClient timeouts, Swagger-dev-only, JWT-secret rotation | Backlog | 2026-07-06 bootstrap |

## Session Continuity

Last session: 2026-07-06
Stopped at: Wrote PROJECT.md, REQUIREMENTS.md, ROADMAP.md, STATE.md from ingested-doc intel (reverse-engineered bootstrap)
Resume file: None
