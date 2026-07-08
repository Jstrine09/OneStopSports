---
phase: 01-backend-service-test-coverage
plan: 02
subsystem: testing
tags: [junit5, mockito, restclient, deep-stubs, espn, football-data-org]

# Dependency graph
requires:
  - phase: 01-backend-service-test-coverage (plan 01)
    provides: package-private test constructors on NflApiService and ExternalApiService (3-arg and 2-arg respectively) that accept pre-built RestClient instances
provides:
  - Unit test coverage locking in NflApiService's scoreboard mapping, standings mapping (conference/division derivation), and career-stats soft-fail contract
  - Unit test coverage locking in ExternalApiService's TOTAL-group standings filter/mapping and football box-score soft-fail contract
affects: [01-03, phase-completion-verification]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "RETURNS_DEEP_STUBS RestClient mocking via package-private test constructors (mirrors NbaApiServiceTest) applied to a 3-RestClient service (NflApiService) and a RestClient+repository service (ExternalApiService)"

key-files:
  created:
    - src/test/java/com/onestopsports/service/NflApiServiceTest.java
    - src/test/java/com/onestopsports/service/ExternalApiServiceTest.java
  modified: []

key-decisions:
  - "Followed the plan exactly: mirrored NbaApiServiceTest's fixture-builder + RETURNS_DEEP_STUBS convention for both new test classes, with no production code changes."

patterns-established:
  - "Pattern: for services with multiple RestClient fields (one per external URL path), mock each RestClient separately and use anyString() for single-string .uri(...) calls vs any(String.class), any(Object[].class) for varargs .uri(template, args) calls — matching the exact overload the service code calls."

requirements-completed: []  # HARD-01 spans all 5 plans in this phase and closes only at phase completion (per plan success criteria) — not marked here.

coverage:
  - id: D1
    description: "NflApiServiceTest covers fetchGameDtosByDate happy-path mapping (FINISHED status, parsed scores, ET timezone, dbLeagueId) and null-body guard"
    requirement: "HARD-01"
    verification:
      - kind: unit
        ref: "src/test/java/com/onestopsports/service/NflApiServiceTest.java#fetchGameDtosByDate_finalGame_mapsStatusScoresTimezoneAndLeagueId"
        status: pass
      - kind: unit
        ref: "src/test/java/com/onestopsports/service/NflApiServiceTest.java#fetchGameDtosByDate_nullBody_returnsEmptyList"
        status: pass
    human_judgment: false
  - id: D2
    description: "NflApiServiceTest covers fetchStandings happy-path (conference + division-derivation + leagueId) and RestClientException soft-fail (empty list)"
    requirement: "HARD-01"
    verification:
      - kind: unit
        ref: "src/test/java/com/onestopsports/service/NflApiServiceTest.java#fetchStandings_twoConferences_mapsRankedEntriesWithConferenceAndDivision"
        status: pass
      - kind: unit
        ref: "src/test/java/com/onestopsports/service/NflApiServiceTest.java#fetchStandings_restClientException_returnsEmptyListWithoutThrowing"
        status: pass
    human_judgment: false
  - id: D3
    description: "NflApiServiceTest covers fetchCareerStats happy-path mapping and RestClientException soft-fail (returns null)"
    requirement: "HARD-01"
    verification:
      - kind: unit
        ref: "src/test/java/com/onestopsports/service/NflApiServiceTest.java#fetchCareerStats_happyPath_mapsCategoriesWithCareerRow"
        status: pass
      - kind: unit
        ref: "src/test/java/com/onestopsports/service/NflApiServiceTest.java#fetchCareerStats_restClientException_returnsNull"
        status: pass
    human_judgment: false
  - id: D4
    description: "ExternalApiServiceTest covers fetchStandings TOTAL-group happy-path mapping, non-TOTAL group filter, and Exception soft-fail (empty list)"
    requirement: "HARD-01"
    verification:
      - kind: unit
        ref: "src/test/java/com/onestopsports/service/ExternalApiServiceTest.java#fetchStandings_totalGroup_mapsEntryWithNullConferenceDivisionPctGamesBehind"
        status: pass
      - kind: unit
        ref: "src/test/java/com/onestopsports/service/ExternalApiServiceTest.java#fetchStandings_noTotalGroup_returnsEmptyList"
        status: pass
      - kind: unit
        ref: "src/test/java/com/onestopsports/service/ExternalApiServiceTest.java#fetchStandings_restClientException_returnsEmptyListWithoutThrowing"
        status: pass
    human_judgment: false
  - id: D5
    description: "ExternalApiServiceTest covers fetchFootballBoxScore soft-fail on RestClientException and on null match-detail body"
    requirement: "HARD-01"
    verification:
      - kind: unit
        ref: "src/test/java/com/onestopsports/service/ExternalApiServiceTest.java#fetchFootballBoxScore_restClientException_returnsNull"
        status: pass
      - kind: unit
        ref: "src/test/java/com/onestopsports/service/ExternalApiServiceTest.java#fetchFootballBoxScore_nullDetailBody_returnsNull"
        status: pass
    human_judgment: false

duration: 15min
completed: 2026-07-08
status: complete
---

# Phase 01 Plan 02: NflApiService + ExternalApiService Unit Tests Summary

**Unit tests for NflApiService (scoreboard/standings/career-stats) and ExternalApiService (standings/box-score) locking in ESPN/football-data.org mapping behavior and the swallow-RestClientException soft-fail contract, using RETURNS_DEEP_STUBS RestClient mocks — zero live network calls.**

## Performance

- **Duration:** 15 min
- **Started:** 2026-07-08T23:03:02Z
- **Completed:** 2026-07-08T23:16:52Z
- **Tasks:** 2
- **Files modified:** 2 (both new test files)

## Accomplishments
- `NflApiServiceTest` (6 tests): proves `fetchGameDtosByDate` maps a STATUS_FINAL event to a MatchDto with status "FINISHED", parsed integer scores, timezone "ET", and the passed-in dbLeagueId; proves the null-body guard returns an empty list; proves `fetchStandings` correctly derives conference + division (via the hardcoded `DIVISION_BY_ABBR` map) and sets leagueId on every team, and that a `RestClientException` is swallowed to an empty list; proves `fetchCareerStats` maps ESPN's stats categories into `PlayerCareerStatsDto` and that a `RestClientException` is swallowed to `null`.
- `ExternalApiServiceTest` (5 tests): proves `fetchStandings` filters to the "TOTAL" standings group (ignoring HOME/AWAY splits) and maps `ApiStandingEntry.draw()` → `StandingsEntryDto.drawn()` with conference/division/pct/gamesBehind all null (football has none of those concepts); proves a non-TOTAL-only response yields an empty list; proves a swallowed exception on the standings call returns an empty list; proves `fetchFootballBoxScore` returns `null` on both a thrown `RestClientException` and a null match-detail body.
- Full backend suite: 108 tests across 15 classes, all green (baseline 97/13 + 11 new tests in 2 new classes — matches expectations from plan 01-01).

## Task Commits

Each task was committed atomically:

1. **Task 1: Write NflApiServiceTest** - `fd2c5d4` (test)
2. **Task 2: Write ExternalApiServiceTest** - `a8740fc` (test)

**Plan metadata:** (this commit, following SUMMARY write)

## Files Created/Modified
- `src/test/java/com/onestopsports/service/NflApiServiceTest.java` - 6 tests covering fetchGameDtosByDate (happy-path + null-body guard), fetchStandings (happy-path conference/division mapping + soft-fail), fetchCareerStats (happy-path + soft-fail)
- `src/test/java/com/onestopsports/service/ExternalApiServiceTest.java` - 5 tests covering fetchStandings (TOTAL-group happy-path + filter + soft-fail), fetchFootballBoxScore (soft-fail on exception + null body)

## Decisions Made
None - plan executed exactly as written. Mirrored the canonical `NbaApiServiceTest` deep-stub convention precisely, including the `.uri(String)` vs `.uri(String, Object...)` matcher distinction (`anyString()` vs `any(String.class), any(Object[].class)`) chosen per the exact overload each production method calls.

## Deviations from Plan

None - plan executed exactly as written. Both test classes were built directly from the plan's `<read_first>` line references and the services' own public response records (no JSON strings), with no production code changes required.

## Issues Encountered

None. Both test classes compiled and passed on the first run; no auto-fixes needed.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Plan 01-03 (remaining wave-2 plan) can proceed independently — no shared state between 01-02 and 01-03 beyond the 01-01 test-constructor foundation.
- HARD-01 remains open (checkbox unchecked in REQUIREMENTS.md) until all 5 plans in this phase land, per the plan's explicit success-criteria instruction.
- Full suite verified green at 108 tests / 15 classes immediately before this summary was written — safe baseline for the next plan in this wave/phase.

---
*Phase: 01-backend-service-test-coverage*
*Completed: 2026-07-08*
