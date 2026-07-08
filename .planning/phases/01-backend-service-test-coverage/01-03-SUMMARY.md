---
phase: 01-backend-service-test-coverage
plan: 03
subsystem: testing
tags: [junit5, mockito, restclient, deep-stubs, api-sports, balldontlie]

# Dependency graph
requires:
  - phase: 01-backend-service-test-coverage (plan 01)
    provides: package-private test constructors on ApiFootballService and BallDontLieService (1-arg each) that accept pre-built RestClient instances, plus BdlPlayersResponse/BdlPlayer records widened to package-private
provides:
  - Unit test coverage locking in ApiFootballService's two-step search→fetch mapping (accent-insensitive name match, FOOTBALL_LABELS stat mapping) and its soft-fail contract
  - Unit test coverage locking in BallDontLieService's first-name-search/last-name-match bio mapping (parsed weight) and its soft-fail contract
affects: [phase-completion-verification]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "RETURNS_DEEP_STUBS RestClient mocking via package-private test constructors (mirrors NbaApiServiceTest), applied to a lambda-based .uri(Function<UriBuilder,URI>) call site (ApiFootballService) using any(Function.class), and a varargs-template .uri(String, Object...) call site (BallDontLieService) using any(String.class), any(Object[].class)"

key-files:
  created:
    - src/test/java/com/onestopsports/service/ApiFootballServiceTest.java
    - src/test/java/com/onestopsports/service/BallDontLieServiceTest.java
  modified: []

key-decisions:
  - "Followed the plan exactly: mirrored NbaApiServiceTest's fixture-builder + RETURNS_DEEP_STUBS convention for both new test classes, with no production code changes."
  - "ApiFootballService's .uri(...) call uses a Function<UriBuilder,URI> lambda rather than a string template, so the mock stub uses any(Function.class) instead of anyString() — matched precisely to the production overload per plan guidance."
  - "BallDontLieServiceTest constructs BdlPlayersResponse/BdlPlayer fixtures directly (no JSON string / Jackson round-trip), proving the 01-01 package-private record widening actually enables same-package test construction."

patterns-established:
  - "Pattern: when a service's RestClient .uri(...) call site takes a Function<UriBuilder,URI> lambda (needed for query-param-heavy endpoints), stub the deep-stub chain with any(Function.class) rather than anyString() — mirrors the anyString() vs any(String.class),any(Object[].class) distinction already established for template vs varargs .uri() call sites in 01-02."

requirements-completed: []  # HARD-01 spans all 5 plans in this phase and closes only at phase completion (per plan success criteria) — not marked here.

coverage:
  - id: D1
    description: "ApiFootballServiceTest covers searchPlayerId happy-path (exact name match, accent-insensitive match) and no-HTTP guard for too-short names"
    requirement: "HARD-01"
    verification:
      - kind: unit
        ref: "src/test/java/com/onestopsports/service/ApiFootballServiceTest.java#searchPlayerId_exactNameMatch_returnsPlayerId"
        status: pass
      - kind: unit
        ref: "src/test/java/com/onestopsports/service/ApiFootballServiceTest.java#searchPlayerId_accentedQueryMatchesPlainAsciiApiName_returnsPlayerId"
        status: pass
      - kind: unit
        ref: "src/test/java/com/onestopsports/service/ApiFootballServiceTest.java#searchPlayerId_nameTooShortToSearch_returnsEmptyWithoutCallingRestClient"
        status: pass
    human_judgment: false
  - id: D2
    description: "ApiFootballServiceTest covers searchPlayerId RestClientException soft-fail (returns Optional.empty)"
    requirement: "HARD-01"
    verification:
      - kind: unit
        ref: "src/test/java/com/onestopsports/service/ApiFootballServiceTest.java#searchPlayerId_restClientThrows_returnsEmptyInsteadOfPropagating"
        status: pass
    human_judgment: false
  - id: D3
    description: "ApiFootballServiceTest covers fetchPlayerStats happy-path mapping to PlayerCareerStatsDto (sport=football, FOOTBALL_LABELS columns, single-season row) and soft-fail on empty response / RestClientException (returns null)"
    requirement: "HARD-01"
    verification:
      - kind: unit
        ref: "src/test/java/com/onestopsports/service/ApiFootballServiceTest.java#fetchPlayerStats_happyPath_mapsToFootballCareerStatsDto"
        status: pass
      - kind: unit
        ref: "src/test/java/com/onestopsports/service/ApiFootballServiceTest.java#fetchPlayerStats_emptyResponse_returnsNull"
        status: pass
      - kind: unit
        ref: "src/test/java/com/onestopsports/service/ApiFootballServiceTest.java#fetchPlayerStats_restClientThrows_returnsNullInsteadOfPropagating"
        status: pass
    human_judgment: false
  - id: D4
    description: "BallDontLieServiceTest covers searchPlayerByName happy-path (first-name-search + last-name-match → PlayerBioDto with parsed integer weight and college/draft fields) and last-name-mismatch returning empty"
    requirement: "HARD-01"
    verification:
      - kind: unit
        ref: "src/test/java/com/onestopsports/service/BallDontLieServiceTest.java#searchPlayerByName_firstNameSearchWithLastNameMatch_mapsToPlayerBioDto"
        status: pass
      - kind: unit
        ref: "src/test/java/com/onestopsports/service/BallDontLieServiceTest.java#searchPlayerByName_lastNameDoesNotMatch_returnsEmpty"
        status: pass
    human_judgment: false
  - id: D5
    description: "BallDontLieServiceTest covers blank/null-name no-HTTP guard and swallowed-exception soft-fail"
    requirement: "HARD-01"
    verification:
      - kind: unit
        ref: "src/test/java/com/onestopsports/service/BallDontLieServiceTest.java#searchPlayerByName_blankName_returnsEmptyWithoutCallingRestClient"
        status: pass
      - kind: unit
        ref: "src/test/java/com/onestopsports/service/BallDontLieServiceTest.java#searchPlayerByName_nullName_returnsEmptyWithoutCallingRestClient"
        status: pass
      - kind: unit
        ref: "src/test/java/com/onestopsports/service/BallDontLieServiceTest.java#searchPlayerByName_restClientThrows_returnsEmptyInsteadOfPropagating"
        status: pass
    human_judgment: false

duration: 12min
completed: 2026-07-08
status: complete
---

# Phase 01 Plan 03: ApiFootballService + BallDontLieService Unit Tests Summary

**Unit tests for ApiFootballService (two-step search→fetch football player stats via api-sports.io) and BallDontLieService (NBA player bio via balldontlie.io) locking in accent-insensitive/first-name-search mapping behavior and the swallow-exception soft-fail contract, using RETURNS_DEEP_STUBS RestClient mocks — zero live network calls.**

## Performance

- **Duration:** 12 min
- **Started:** 2026-07-08T19:19:00Z
- **Completed:** 2026-07-08T19:23:35Z
- **Tasks:** 2
- **Files modified:** 2 (both new test files)

## Accomplishments
- `ApiFootballServiceTest` (7 tests): proves `searchPlayerId` returns the matched player's API-SPORTS id on an exact "firstname lastname" match, that an accented query ("Ousmane Dembélé") still matches a plain-ASCII API entry ("Dembele") via the accent-strip comparison, that a too-short name (`"Al Bo"`) returns `Optional.empty()` without ever touching the mocked `RestClient` (verified via `verifyNoInteractions`), and that a `RestClientException` is swallowed to `Optional.empty()`. Proves `fetchPlayerStats` maps a single `ApiStatBlock` into a `PlayerCareerStatsDto` with `sport="football"`, one `StatCategory` using the exact `FOOTBALL_LABELS` column order, a null career row (free-tier is single-season), and a season row whose team/competition/values line up with the fixture; proves it returns `null` on both an empty API response and a swallowed `RestClientException`.
- `BallDontLieServiceTest` (5 tests): proves `searchPlayerByName("Jaylen Brown")` searches by first name and, on finding a result whose `lastName` matches, maps every field through to `PlayerBioDto` — including parsing the API's string `weight` ("223") into an `Integer`; proves a first-name hit with a non-matching last name returns `Optional.empty()` (no wrong-match); proves both a blank and a `null` name short-circuit before any HTTP call (`verifyNoInteractions`); proves a thrown exception is swallowed to `Optional.empty()`. Fixtures construct `BdlPlayersResponse`/`BdlPlayer` directly (the 01-01 package-private record widening), with no JSON string/Jackson round-trip needed.
- Full backend suite: 120 tests across 17 classes, all green (baseline 108/15 classes + 12 new tests in 2 new classes — matches expectations from plan 01-01/01-02).

## Task Commits

Each task was committed atomically:

1. **Task 1: Write ApiFootballServiceTest** - `2eae7f6` (test)
2. **Task 2: Write BallDontLieServiceTest** - `849c0ba` (test)

**Plan metadata:** (this commit, following SUMMARY write)

## Files Created/Modified
- `src/test/java/com/onestopsports/service/ApiFootballServiceTest.java` - 7 tests covering searchPlayerId (exact match, accent-insensitive match, too-short no-HTTP guard, RestClientException soft-fail) and fetchPlayerStats (FOOTBALL_LABELS happy-path mapping, empty-response and RestClientException soft-fail)
- `src/test/java/com/onestopsports/service/BallDontLieServiceTest.java` - 5 tests covering searchPlayerByName (first-name-search/last-name-match happy path with parsed weight, last-name mismatch, blank/null no-HTTP guard, exception soft-fail)

## Decisions Made
None - plan executed exactly as written. The only notable implementation choice (not a deviation, called out explicitly in the plan's `<read_first>`) was stubbing `ApiFootballService`'s `.uri(...)` call with `any(Function.class)` instead of `anyString()`, since that production call site builds the URI via a `Function<UriBuilder, URI>` lambda (needed for its multiple query params) rather than a plain string template — matching the exact overload the service code calls, the same discipline established in 01-02 for template vs varargs `.uri()` forms.

## Deviations from Plan

None - plan executed exactly as written. Both test classes were built directly from the plan's `<read_first>` line references and the services' own public/package-private response records (no JSON strings), with no production code changes required.

## Issues Encountered

None. Both test classes compiled and passed on the first run; no auto-fixes needed.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Wave 2 of Phase 01 (plans 01-02, 01-03, 01-04, 01-05 in parallel per ROADMAP) — 01-03 is now complete and independent of the other wave-2 plans beyond the shared 01-01 test-constructor foundation.
- HARD-01 remains open (checkbox unchecked in REQUIREMENTS.md) until all 5 plans in this phase land, per the plan's explicit success-criteria instruction. This plan does NOT mark it complete.
- Full suite verified green at 120 tests / 17 classes immediately before this summary was written — safe baseline for the next plan in this wave/phase.

---
*Phase: 01-backend-service-test-coverage*
*Completed: 2026-07-08*

## Self-Check: PASSED

- FOUND: src/test/java/com/onestopsports/service/ApiFootballServiceTest.java
- FOUND: src/test/java/com/onestopsports/service/BallDontLieServiceTest.java
- FOUND: .planning/phases/01-backend-service-test-coverage/01-03-SUMMARY.md
- FOUND commit: 2eae7f6 (test(01-03): add ApiFootballServiceTest with happy-path + soft-fail coverage)
- FOUND commit: 849c0ba (test(01-03): add BallDontLieServiceTest with happy-path + soft-fail coverage)
