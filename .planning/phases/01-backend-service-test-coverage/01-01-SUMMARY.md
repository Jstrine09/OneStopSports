---
phase: 01-backend-service-test-coverage
plan: 01
subsystem: testing
tags: [java, spring-boot, restclient, junit, mockito, test-seams]

# Dependency graph
requires: []
provides:
  - "Package-private test constructors on NflApiService, ExternalApiService, ApiFootballService, BallDontLieService"
  - "@Autowired disambiguation on all four services' production @Value constructors"
  - "Package-private BdlPlayersResponse/BdlPlayer records in BallDontLieService"
affects: [01-02-PLAN, 01-03-PLAN, "backend-service-test-coverage wave 2 adapter test plans"]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Package-private test constructor: a second, package-visible constructor that accepts pre-built RestClient instances directly (bypassing @Value resolution), paired with @Autowired on the production constructor so Spring keeps resolving it unambiguously. Mirrors the existing NbaApiService convention."
    - "Package-private response records: inner records mirroring third-party JSON shapes are declared package-private (not private) so same-package unit tests can construct fixture instances; Java 21 makes a private record's canonical constructor private too, which would block this."

key-files:
  created: []
  modified:
    - src/main/java/com/onestopsports/service/NflApiService.java
    - src/main/java/com/onestopsports/service/ExternalApiService.java
    - src/main/java/com/onestopsports/service/ApiFootballService.java
    - src/main/java/com/onestopsports/service/BallDontLieService.java

key-decisions:
  - "Followed the plan exactly: replicated NbaApiService's existing test-constructor + @Autowired shape onto the four remaining external-API services, with no behavior changes."

patterns-established:
  - "Package-private test constructor: see tech-stack.patterns above."
  - "Package-private response records: see tech-stack.patterns above."

requirements-completed: [HARD-01]

coverage:
  - id: D1
    description: "NflApiService, ExternalApiService, and ApiFootballService each gain a package-private test constructor accepting pre-built RestClient instances, with @Autowired added to their existing production @Value constructor."
    requirement: "HARD-01"
    verification:
      - kind: integration
        ref: "mvn -q test -Dtest=OneStopSportsApplicationTests (full Spring context load proves Spring still resolves each production constructor after a second constructor was added)"
        status: pass
    human_judgment: false
  - id: D2
    description: "BallDontLieService gains a package-private test constructor and @Autowired on its production constructor; BdlPlayersResponse and BdlPlayer are widened from private to package-private records so same-package tests can build fixtures."
    requirement: "HARD-01"
    verification:
      - kind: integration
        ref: "mvn -q test -Dtest=OneStopSportsApplicationTests"
        status: pass
      - kind: unit
        ref: "mvn -q test (full baseline suite — 66 tests / 9 classes, no regression)"
        status: pass
    human_judgment: false

duration: 20min
completed: 2026-07-08
status: complete
---

# Phase 01 Plan 01: External-API Service Test Seams Summary

**Package-private test constructors added to NflApiService, ExternalApiService, ApiFootballService, and BallDontLieService (mirroring the existing NbaApiService pattern), plus package-private visibility on BallDontLieService's response records — unblocking Wave 2 adapter unit tests with zero behavior change.**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-07-08T22:27Z (following prior phase-plan commit)
- **Completed:** 2026-07-08T22:47Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- All four currently-untestable external-API services (NflApiService, ExternalApiService, ApiFootballService, BallDontLieService) now have a package-private constructor that accepts pre-built RestClient instances, enabling Wave 2 unit tests to inject mocks without a live HTTP server or Spring context.
- Each service's existing public `@Value`-driven production constructor is annotated `@Autowired` so Spring unambiguously picks it once a second constructor exists — verified by the full-context `OneStopSportsApplicationTests` load still passing.
- `BallDontLieService`'s `BdlPlayersResponse` and `BdlPlayer` inner records were widened from `private` to package-private, so a same-package test class can construct fixture instances (a Java 21 `private record`'s canonical constructor is also private, which would otherwise block this).
- No method bodies, field types, or public APIs changed on any of the four services. No new dependencies. No new test files — this plan only adds the seams Wave 2 plans (01-02, 01-03) will compile against.

## Task Commits

Each task was committed atomically:

1. **Task 1: Add package-private test constructors to NflApiService, ExternalApiService, ApiFootballService** - `c0089f0` (refactor)
2. **Task 2: Add BallDontLieService test constructor and widen its response records** - `ea3ca08` (refactor)

**Plan metadata:** (this commit, following)

## Files Created/Modified
- `src/main/java/com/onestopsports/service/NflApiService.java` - `@Autowired` on production constructor; new package-private `NflApiService(RestClient, RestClient, RestClient)` test constructor with junior-dev comment
- `src/main/java/com/onestopsports/service/ExternalApiService.java` - `@Autowired` on production constructor; new package-private `ExternalApiService(RestClient, LeagueRepository)` test constructor with junior-dev comment
- `src/main/java/com/onestopsports/service/ApiFootballService.java` - `@Autowired` on production constructor; new package-private `ApiFootballService(RestClient)` test constructor with junior-dev comment
- `src/main/java/com/onestopsports/service/BallDontLieService.java` - `@Autowired` on production constructor; new package-private `BallDontLieService(RestClient)` test constructor with junior-dev comment; `BdlPlayersResponse` and `BdlPlayer` records widened from `private` to package-private with an updated comment explaining why

## Decisions Made
- None beyond the plan itself - plan executed exactly as written. The plan's own `key_links` note (that `@Autowired` on the production constructor is what lets Spring disambiguate once a test constructor exists) was applied identically to all four services, replicating the pre-existing `NbaApiService` convention.

## Deviations from Plan

None - plan executed exactly as written. Both tasks matched their acceptance criteria on the first pass; no auto-fixes, no blocking issues, no architectural questions.

## Issues Encountered

None. The `OneStopSportsApplicationTests` context-load test makes real outbound calls to football-data.org (which fails loudly and logs an expected `ERROR` — the app's `DataLoader` is deliberately fail-open/loud on an invalid key) and to ESPN (which succeeds, seeding real NBA/NFL data into the H2 in-memory DB). This is pre-existing baseline behavior of that specific test class, unrelated to and unmodified by this plan; the `ERROR` log lines seen during verification are expected and were present before this plan's changes too. The four services touched by this plan (`NflApiService`, `ExternalApiService`, `ApiFootballService`, `BallDontLieService`) are not exercised by that context-load test at all — the test only proves Spring can still construct each bean via the annotated production constructor.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Wave 2 plans (01-02, 01-03) can now compile unit tests against `NflApiServiceTest`, `ExternalApiServiceTest`, `ApiFootballServiceTest`, and `BallDontLieServiceTest` using the `RestClient`-mocking pattern already proven by `NbaApiServiceTest`.
- Baseline suite remains green: 66 tests across 9 classes, verified via `mvn test` after both tasks landed (see per-class breakdown: `OneStopSportsApplicationTests`=1, `AuthServiceTest`=6, `AuthControllerTest`=7, `MatchServiceTest`=13, `LeagueServiceTest`=9, `PlayerServiceCareerStatsTest`=9, `NbaApiServiceTest`=13, `TeamServiceTest`=3, `TextNormalizerTest`=5).
- No blockers for Wave 2.

---
*Phase: 01-backend-service-test-coverage*
*Completed: 2026-07-08*
