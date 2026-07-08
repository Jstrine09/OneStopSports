---
phase: 01-backend-service-test-coverage
plan: 05
subsystem: backend-testing
tags: [testing, player-service, global-exception-handler, mockmvc, mockito]
dependency-graph:
  requires: []
  provides:
    - PlayerServiceTest (resolvePhotoUrl / toDto / searchPlayers coverage)
    - GlobalExceptionHandlerTest (dispatch-order + handler-mapping coverage)
  affects:
    - Phase 01 HARD-01 (backend service test coverage) — this is the final plan of the phase
tech-stack:
  added: []
  patterns:
    - "MockMvcBuilders.standaloneSetup(...).setControllerAdvice(...) to prove real Spring @ExceptionHandler dispatch resolution (not just method-body assertions)"
    - "Exercising a private method (resolvePhotoUrl) indirectly through its public entry point (getPlayerById/toDto) rather than reflection"
key-files:
  created:
    - src/test/java/com/onestopsports/service/PlayerServiceTest.java
    - src/test/java/com/onestopsports/controller/GlobalExceptionHandlerTest.java
  modified: []
decisions:
  - "PlayerServiceTest is a NEW file, separate from the existing PlayerServiceCareerStatsTest, keeping each test class scoped to a distinct group of PlayerService methods per the plan's naming guidance."
  - "resolvePhotoUrl (private) is tested only through getPlayerById's public toDto mapping — no reflection, matching the existing repo convention of testing through public entry points."
  - "GlobalExceptionHandlerTest hybrid: standaloneSetup + a test-only ThrowingTestController for the two dispatch-order assertions that Spring's real @ExceptionHandler resolver must decide, and plain direct-call ResponseEntity assertions for the remaining six handlers whose own status/body logic was in question, not dispatch precedence."
metrics:
  duration: "~15 min"
  completed: 2026-07-08
status: complete
---

# Phase 01 Plan 05: PlayerService photo/DTO/search + GlobalExceptionHandler dispatch coverage Summary

Added the two remaining test classes that complete HARD-01 for the backend-service-test-coverage phase: a new `PlayerServiceTest` covering `resolvePhotoUrl`, `toDto`, and `searchPlayers` (the three `PlayerService` methods `PlayerServiceCareerStatsTest` doesn't touch), and a `GlobalExceptionHandlerTest` that proves the `ResponseStatusException`-before-`Exception`-catch-all dispatch precedence via real Spring MVC dispatch (`MockMvcBuilders.standaloneSetup`), plus direct-call coverage of the remaining six handler methods.

## What Was Built

### Task 1 — `PlayerServiceTest.java`
New file in `src/test/java/com/onestopsports/service/`, mirroring `PlayerServiceCareerStatsTest`'s `@ExtendWith(MockitoExtension.class)` / `@InjectMocks PlayerService` shape with the same five mocked collaborators (`PlayerRepository`, `BallDontLieService`, `NbaApiService`, `NflApiService`, `ApiFootballService`). A private `playerWithPhoto(...)` fixture helper builds a `Player → Team → Sport` chain (mirroring the existing `playerInSport` helper) with an added `photoUrl` control knob.

All three untested public-surface behaviors are exercised through `getPlayerById` (which calls `toDto`, which calls the private `resolvePhotoUrl`):
- **Persisted-photoUrl-wins** — a player with a stored `photoUrl` returns exactly that URL even when a basketball sport + externalId is also present (proves Layer 1 short-circuits before Layer 2).
- **NBA CDN derivation** — basketball + externalId, no stored photo → `https://a.espncdn.com/i/headshots/nba/players/full/{id}.png`.
- **NFL CDN derivation** — american-football + externalId, no stored photo → the `.../nfl/...` equivalent.
- **Null cases** — no externalId and no photo (football/soccer) → null; and externalId present but sport outside the NBA/NFL switch → null.
- **toDto field mapping** — asserts every non-photo `PlayerDto` field (id, name, position, nationality, dateOfBirth, jerseyNumber) plus `teamId`.
- **searchPlayers** — stubs `findByNameNormalizedContaining` to return 12 players and asserts the result is capped at exactly 10 (the `.limit(10)` behavior), plus a query-normalization assertion (`TextNormalizer.normalize("Dembélé")` is the exact stub argument the repository call must match) and an empty-results case.

8 test methods total.

### Task 2 — `GlobalExceptionHandlerTest.java`
New file in `src/test/java/com/onestopsports/controller/`. Follows the RESEARCH.md-recommended hybrid pattern:

- A private static `ThrowingTestController` (test-only `@RestController`) with two endpoints — one throws `ResponseStatusException(NOT_FOUND, ...)`, the other a raw `RuntimeException`.
- `MockMvc` built via `MockMvcBuilders.standaloneSetup(new ThrowingTestController()).setControllerAdvice(new GlobalExceptionHandler())` — no `@WebMvcTest`, no Spring context, no security filters.
- **Dispatch-order test 1**: hitting `/test/not-found` asserts `404` and `$.message == "Player not found: 999"` — proving Spring's real `@ExceptionHandler` resolver picks `handleResponseStatus` over the catch-all.
- **Dispatch-order test 2**: hitting `/test/boom` asserts `500` and `$.message == "An unexpected error occurred"`, plus an explicit assertion that the raw exception text ("secret detail") is absent from the response body — pinning the information-disclosure mitigation (threat T-01-06) as a regression guard.
- **Direct-call tests** (six): `new GlobalExceptionHandler()` invoked directly for `handleBadCredentials` (401, vague message), `handleAccessDenied` (403), `handleMethodNotSupported` (405, verb in message), `handleMissingParam` (400, parameter name in message), `handleUnreadable` (400, generic malformed-body message), `handleDataIntegrity` (409, raw SQL detail NOT leaked).

8 test methods total.

## Verification

- `mvn -q test -Dtest=PlayerServiceTest` — exit 0.
- `mvn -q test -Dtest=GlobalExceptionHandlerTest` — exit 0 (the `[GlobalExceptionHandler] Unhandled exception: ...` ERROR line in the console for the catch-all test is expected server-side logging behavior from `handleGeneric`'s `log.error(...)` call, not a test failure).
- Full suite (`mvn -q test`): exit 0, **97 tests across 13 classes, 0 failures, 0 errors, 0 skipped** — up from the 81/11 baseline going into this plan (66 original + 15 from 01-01/01-04), confirming the expected +16 new tests (8 + 8) with zero regressions.

## Deviations from Plan

None — plan executed exactly as written. Both tasks matched their acceptance criteria on the first pass; no auto-fixes, no architectural questions, no auth gates.

## Requirements

HARD-01 is NOT marked complete by this plan per the execution instructions — it spans all 5 plans in this phase and closes only at full phase completion (all 5 plans done). This was plan 5 of 5.

## Self-Check: PASSED

- FOUND: src/test/java/com/onestopsports/service/PlayerServiceTest.java
- FOUND: src/test/java/com/onestopsports/controller/GlobalExceptionHandlerTest.java
- FOUND commit 22d39b8 (PlayerServiceTest)
- FOUND commit b6d8c86 (GlobalExceptionHandlerTest)
