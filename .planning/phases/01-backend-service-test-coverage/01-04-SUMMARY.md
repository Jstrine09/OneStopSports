---
phase: 01-backend-service-test-coverage
plan: 04
subsystem: testing
tags: [java, spring-boot, junit, mockito, unit-tests, user-service, sport-service]

# Dependency graph
requires: []
provides:
  - "UserServiceTest — unit coverage of UserService's favourite-CRUD guard clauses and DTO-mapping delegation"
  - "SportServiceTest — unit coverage of SportService's listing and slug-lookup 404 guard"
affects: ["01-05-PLAN (phase-wide suite verification at wave merge)"]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Plain @ExtendWith(MockitoExtension.class) + @Mock/@InjectMocks convention (MatchServiceTest style) applied to a 7-dependency service (UserService) and a 1-dependency service (SportService) — no test constructor needed since both production constructors already accept pure Spring-bean types (repositories + sibling services)."

key-files:
  created:
    - src/test/java/com/onestopsports/service/UserServiceTest.java
    - src/test/java/com/onestopsports/service/SportServiceTest.java
  modified: []

key-decisions:
  - "Followed the plan exactly: both test classes use the plain @InjectMocks/@Mock convention with no test constructor, per the plan's premise that both services' constructors already accept pure Spring-bean types."

patterns-established: []

requirements-completed: []

coverage:
  - id: D1
    description: "UserServiceTest covers getCurrentUser (happy path + 404 on unknown username), addFavoriteTeam (skip-if-exists, 404-on-missing, happy-path save), addFavoritePlayer (symmetric 404 + skip-if-exists), getFavoriteTeams/getFavoritePlayers (DTO-mapping delegation to TeamService/PlayerService), and removeFavoriteTeam/removeFavoritePlayer (delegation to the repository's deleteByUserIdAnd*Id)."
    requirement: "HARD-01"
    verification:
      - kind: unit
        ref: "mvn -q test -Dtest=UserServiceTest (11 tests, all pass)"
        status: pass
    human_judgment: false
  - id: D2
    description: "SportServiceTest covers getAllSports (entity-to-DTO field mapping + empty-list case) and getSportBySlug (happy path + 404 on unknown slug)."
    requirement: "HARD-01"
    verification:
      - kind: unit
        ref: "mvn -q test -Dtest=SportServiceTest (4 tests, all pass)"
        status: pass
      - kind: unit
        ref: "mvn -q test (full suite — baseline 66 + these 15 new = 81 tests / 11 classes, no regression)"
        status: pass
    human_judgment: false

duration: ~15min
completed: 2026-07-08
status: complete
---

# Phase 01 Plan 04: UserService + SportService Unit Tests Summary

**Pure Mockito unit tests for UserService's favourite-CRUD guard clauses (skip-if-already-favourited, 404-on-missing, delegation to TeamService/PlayerService toDto) and SportService's listing + slug-lookup 404 guard — no Spring context, no database, no HTTP.**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-07-08T22:49Z (following 01-01-PLAN.md completion)
- **Completed:** 2026-07-08T22:54Z
- **Tasks:** 2
- **Files modified:** 2 (both new test files)

## Accomplishments

- `UserServiceTest` (11 tests) exercises all seven of `UserService`'s constructor dependencies (`UserRepository`, `TeamRepository`, `PlayerRepository`, `FavoriteTeamRepository`, `FavoritePlayerRepository`, `TeamService`, `PlayerService`) as plain `@Mock` fields with `@InjectMocks UserService` — mirroring the `MatchServiceTest` convention exactly, with no test constructor needed.
- Pins the `findUser` 404 guard (unknown username → `ResponseStatusException` with "User not found" in the message) via `getCurrentUser`.
- Pins the "skip if already favourited" guard on both `addFavoriteTeam` and `addFavoritePlayer` — when `existsByUserIdAndTeamId`/`existsByUserIdAndPlayerId` returns `true`, the test asserts the repository's `findById` and `save` are `never()` called, proving no wasted lookup and no duplicate-row risk.
- Pins the "team/player not found" 404 guard on the genuinely-new-favourite path for both team and player, and a happy-path test asserting exactly one `FavoriteTeam` row is saved (`verify(..., times(1)).save(...)`).
- Pins `getFavoriteTeams`/`getFavoritePlayers` as pure delegation to `TeamService.toDto`/`PlayerService.toDto` — the test stubs the mocked mapper to return a known DTO and asserts that exact DTO instance flows through unchanged, proving `UserService` doesn't build DTOs itself.
- Pins `removeFavoriteTeam`/`removeFavoritePlayer` as delegation to `favoriteTeamRepository.deleteByUserIdAndTeamId`/`favoritePlayerRepository.deleteByUserIdAndPlayerId` with the **resolved user id** (not the raw username) and the target id.
- `SportServiceTest` (4 tests) mocks the single `SportRepository` dependency and covers `getAllSports` (two-entity mapping preserving id/name/slug/iconUrl, plus the empty-list case) and `getSportBySlug` (happy path returning the mapped DTO, and a 404 with "Sport not found" in the message on a missing slug).
- Full suite verified green: baseline 66 tests / 9 classes + these 15 new tests / 2 classes = **81 tests / 11 classes, 0 failures, 0 errors** (`mvn test`).

## Task Commits

Each task was committed atomically:

1. **Task 1: Write UserServiceTest** - `8d82ccd` (test)
2. **Task 2: Write SportServiceTest** - `6bcc69a` (test)

**Plan metadata:** (this commit, following)

## Files Created/Modified
- `src/test/java/com/onestopsports/service/UserServiceTest.java` (new, 11 tests) - `@ExtendWith(MockitoExtension.class)`, `@Mock` for all 7 `UserService` dependencies, `@InjectMocks UserService`; covers `getCurrentUser`, `addFavoriteTeam`, `addFavoritePlayer`, `getFavoriteTeams`, `getFavoritePlayers`, `removeFavoriteTeam`, `removeFavoritePlayer`. Every test method carries a plain-English inline comment per the project's junior-developer-comment hard rule.
- `src/test/java/com/onestopsports/service/SportServiceTest.java` (new, 4 tests) - `@ExtendWith(MockitoExtension.class)`, `@Mock SportRepository`, `@InjectMocks SportService`; covers `getAllSports` and `getSportBySlug`. Same inline-comment convention applied.

## Decisions Made
- None beyond the plan itself — plan executed exactly as written. Both tests used the plain `@InjectMocks`/`@Mock` convention with no test constructor, confirming the plan's premise that both services' constructors already accept pure Spring-bean types (repositories + sibling services), unlike the external-API adapters in 01-01/01-02/01-03 which needed a package-private `RestClient` test constructor.

## Deviations from Plan

None - plan executed exactly as written. Both tasks matched their acceptance criteria on the first pass (per-class `mvn -q test -Dtest=<ClassName>` exited 0 for each); no auto-fixes, no blocking issues, no architectural questions. `TeamService.toDto` and `PlayerService.toDto` are package-private methods — since both test classes live in the same `com.onestopsports.service` package as `MatchServiceTest`, stubbing them via Mockito required no visibility workaround.

## Issues Encountered

None. No auth gates, no missing dependencies, no flaky tests.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- This plan's tests are independent of the other Wave-1/Wave-2 plans in this phase (`depends_on: []`) — no blockers for `01-05-PLAN` (the phase-wide suite verification wave).
- Full suite verified green after this plan: 81 tests across 11 classes (`OneStopSportsApplicationTests`=1, `AuthServiceTest`=6, `AuthControllerTest`=7, `MatchServiceTest`=13, `LeagueServiceTest`=9, `PlayerServiceCareerStatsTest`=9, `NbaApiServiceTest`=13, `TeamServiceTest`=3, `TextNormalizerTest`=5, `UserServiceTest`=11, `SportServiceTest`=4).
- HARD-01 is NOT marked complete by this plan alone — per the phase's requirement span, it closes only when all 5 plans in this phase land.

---
*Phase: 01-backend-service-test-coverage*
*Completed: 2026-07-08*

## Self-Check: PASSED

Both new test files exist on disk (`src/test/java/com/onestopsports/service/UserServiceTest.java`, `src/test/java/com/onestopsports/service/SportServiceTest.java`); task commits `8d82ccd` and `6bcc69a` both found in `git log`.
