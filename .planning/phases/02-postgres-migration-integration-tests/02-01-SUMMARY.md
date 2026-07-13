---
phase: 02-postgres-migration-integration-tests
plan: 01
subsystem: testing
tags: [testcontainers, flyway, postgres, junit5, maven, integration-test]

# Dependency graph
requires:
  - phase: 01-backend-service-test-coverage
    provides: the 120-test H2 baseline this plan must not regress
provides:
  - "Real-Postgres Testcontainers harness (postgres:16-alpine) for the V1-V9 Flyway chain"
  - "Opt-in `mvn verify -Pintegration` Maven profile, Docker-free by default"
  - "PostgresMigrationIT with a reusable two-stage (target 8 -> seedFixtures -> target 9) migration pattern and seedFixtures helper, ready for plan 02-02 to extend with the duplicate-club merge fixture"
affects: [02-02-postgres-migration-integration-tests]

# Tech tracking
tech-stack:
  added: [org.testcontainers:postgresql, org.testcontainers:junit-jupiter]
  patterns:
    - "Plain-JUnit5 (no @SpringBootTest) Testcontainers IT to avoid firing ApplicationRunner-based live-API loaders"
    - "Two-stage Flyway .target() migration with a shared JDBC URL to inject fixtures mid-chain"
    - "Maven Failsafe wrapped in an opt-in profile so *IT.java classes never run under default `mvn test`"

key-files:
  created:
    - src/test/java/com/onestopsports/migration/PostgresMigrationIT.java
  modified:
    - pom.xml

key-decisions:
  - "Followed RESEARCH.md's primary recommendation: plain JUnit5 + @Testcontainers + raw Flyway/JdbcTemplate, no Spring context, to avoid DataLoader/NbaDataLoader/NflDataLoader firing live API calls at context startup."
  - "seedFixtures for this plan seeds only a non-duplicate V8-shape fixture (one sport/league/team/player, external_id NULL so V9's merge never groups it); the exhaustive duplicate-club merge fixture is deferred to plan 02-02 per the plan's explicit scope split."
  - "Local Docker Desktop 4.81 (API 1.55, MinAPIVersion 1.40) rejects docker-java's default API-version negotiation (calls /v1.24/info -> HTTP 400). Worked around locally by passing -DargLine=\"-Dapi.version=1.41\" on the mvn command line; no pom.xml change was made since the plan's acceptance criteria explicitly forbid adding <configuration> to the failsafe plugin declaration. See Deviations."

requirements-completed: []  # HARD-02 spans both plans; not marked complete until phase completion (per plan's explicit instruction)

coverage:
  - id: D1
    description: "mvn test stays green (120 H2 tests) and Docker-free after adding Testcontainers deps + integration profile"
    requirement: "HARD-02"
    verification:
      - kind: unit
        ref: "mvn -q test (all 17 test classes, 120 tests)"
        status: pass
    human_judgment: false
  - id: D2
    description: "mvn verify -Pintegration -Dit.test=PostgresMigrationIT boots real postgres:16-alpine, runs the full V1->V9 chain, and all 6 assertions pass"
    requirement: "HARD-02"
    verification:
      - kind: integration
        ref: "mvn verify -Pintegration -Dit.test=PostgresMigrationIT -DargLine=\"-Dapi.version=1.41\" (6/6 tests, BUILD SUCCESS)"
        status: pass
    human_judgment: false

duration: 32min
completed: 2026-07-13
status: complete
---

# Phase 2 Plan 1: Postgres Migration Integration Test Harness Summary

**Testcontainers-backed `PostgresMigrationIT` runs the real V1->V9 Flyway chain against postgres:16-alpine and proves V8's name_normalized columns/indexes/round-trip plus V9's schema-shape guarantees (league_id dropped, sport_id NOT NULL+FK, team_league populated), gated behind an opt-in `mvn verify -Pintegration` Maven profile that keeps the default `mvn test` at 120 tests, Docker-free.**

## Performance

- **Duration:** 32 min
- **Started:** 2026-07-13T12:24:18-04:00 (phase plan commit)
- **Completed:** 2026-07-13T12:56:01-04:00
- **Tasks:** 2/2
- **Files modified:** 2 (1 created, 1 modified)

## Accomplishments
- Added `org.testcontainers:postgresql` and `org.testcontainers:junit-jupiter` (test scope, version-managed by `spring-boot-starter-parent:3.4.4`, zero explicit version pins) plus an opt-in `integration` Maven profile wrapping a bare `maven-failsafe-plugin` declaration.
- Created `PostgresMigrationIT` — a plain-JUnit5 (no `@SpringBootTest`) class that boots a real `postgres:16-alpine` Testcontainers instance, runs Flyway to target `8`, seeds a fixture via raw JDBC (bypassing `@PrePersist`, using `TextNormalizer.normalize()` directly for the expected value), then runs Flyway to target `9` on the same connection.
- Six `@Test` methods pass against real Postgres: full-chain-to-V9, V8 columns+indexes, V8 accent-strip round-trip, V9 `league_id` dropped, V9 `sport_id` NOT NULL + `fk_team_sport`, V9 `team_league` populated.
- Verified `mvn test` (no profile) stays green at 120 tests and requires no Docker daemon — the new `*IT.java` class is invisible to Surefire's default include pattern.

## Task Commits

Each task was committed atomically:

1. **Task 1: Add Testcontainers test dependencies and the opt-in `integration` Maven profile to pom.xml** - `73f376b` (build)
2. **Task 2: Create PostgresMigrationIT with two-stage V1->V9 migrate + V8 and V9-schema assertions** - `1a73f88` (test)

**Plan metadata:** (this commit, following SUMMARY.md)

## Files Created/Modified
- `pom.xml` - Added two version-less Testcontainers test-scope dependencies and a new `<profiles><profile id="integration">` block wrapping a bare `maven-failsafe-plugin` declaration; existing `<build>` block untouched.
- `src/test/java/com/onestopsports/migration/PostgresMigrationIT.java` - New plain-JUnit5 Testcontainers class: two-stage Flyway migrate (`target("8")` → `seedFixtures` → `target("9")`) against a real `postgres:16-alpine` container, with six read-only JdbcTemplate assertions against `information_schema`/`pg_indexes`/`flyway_schema_history`.

## Decisions Made
- Plain JUnit5 + `@Testcontainers`, no Spring context — matches RESEARCH.md's primary recommendation and avoids `DataLoader`/`NbaDataLoader`/`NflDataLoader` `ApplicationRunner`s firing live API calls at context startup.
- `seedFixtures` in this plan seeds only the non-duplicate V8/schema-shape fixture (one sport, one league, one team with an accented name and NULL `external_id`, one player) — the exhaustive duplicate-club merge scenario (favourites re-pointing, player de-duplication) is explicitly deferred to plan 02-02, which will extend this same method and add the remaining `@Test` methods from RESEARCH.md's Validation Architecture table.
- `external_id` was left NULL on the seeded team specifically so V9's `(sport_id, external_id)` merge grouping (`WHERE external_id IS NOT NULL`) never touches it — the row must survive V9 completely unchanged to prove the join-table backfill and schema-shape assertions cleanly.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Local Docker Desktop rejected docker-java's default API-version negotiation**
- **Found during:** Task 2 verification (`mvn verify -Pintegration -Dit.test=PostgresMigrationIT`)
- **Issue:** The first verification attempt failed before any container started: Testcontainers' bundled (shaded) docker-java client defaults to Docker API version `1.24` when probing `/info`. This machine's Docker Desktop 4.81.0 (server API 1.55, `MinAPIVersion: 1.40`) rejects that legacy version with an HTTP 400 and an empty/default JSON body, so Testcontainers reported "Could not find a valid Docker environment" even though `docker version`/`docker info`/`docker ps` all worked fine via the CLI. Confirmed via direct `curl --unix-socket /var/run/docker.sock http://localhost/v1.24/info` → 400, vs `http://localhost/v1.40/info` → 200.
- **Fix:** No code change — this is a local Docker Desktop configuration mismatch, not a defect in `pom.xml` or `PostgresMigrationIT.java`. Passed the JVM system property `-Dapi.version=1.41` via Failsafe's `argLine` (`mvn verify -Pintegration -Dit.test=PostgresMigrationIT -DargLine="-Dapi.version=1.41"`) to force docker-java to negotiate at a version the daemon accepts. Did NOT add `<configuration>`/`<systemPropertyVariables>` to the `maven-failsafe-plugin` declaration in `pom.xml`, since the plan's Task 1 acceptance criteria explicitly require the plugin be declared with "no `<version>` and no `<configuration>`" — that constraint is honored as written.
- **Files modified:** None (command-line-only workaround, not a repo change).
- **Verification:** With the flag, `mvn verify -Pintegration -Dit.test=PostgresMigrationIT -DargLine="-Dapi.version=1.41"` produces `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- **Committed in:** N/A (no commit needed — command-line flag only).
- **Note for future runs on this or similar machines:** if `mvn verify -Pintegration -Dit.test=PostgresMigrationIT` reports "Could not find a valid Docker environment" despite Docker Desktop running, append `-DargLine="-Dapi.version=1.41"` (or any API version ≥ the daemon's `MinAPIVersion`, checkable via `docker version`). This is a known Testcontainers/docker-java API-version-negotiation gap with newer Docker Desktop releases, not a bug in this project's code.

---

**Total deviations:** 1 auto-fixed (1 blocking — local environment workaround, no code change)
**Impact on plan:** No scope creep. The plan's literal verify command (`mvn -q verify -Pintegration -Dit.test=PostgresMigrationIT`) is exactly what CI and other Docker Desktop versions will run unmodified; this machine's specific Docker Desktop build needed one extra command-line flag to demonstrate the harness works end-to-end against real Postgres. All 6 assertions passed against the real container in this session.

## Issues Encountered
None beyond the Docker API-version negotiation deviation documented above.

## User Setup Required
None - no external service configuration required. Running the integration suite requires a local Docker daemon (already confirmed available in this session); see the Deviations note above if `mvn verify -Pintegration` reports no valid Docker environment despite Docker Desktop running.

## Next Phase Readiness
- `PostgresMigrationIT`'s `seedFixtures` method and two-stage migration `@BeforeAll` are in place and ready for plan 02-02 to extend with the duplicate-club fixture (two teams sharing `(sport_id, external_id)`, duplicate players, `favorite_team`/`favorite_player` re-pointing + collision-skip branches) and the remaining V9 data-merge `@Test` methods from RESEARCH.md's Validation Architecture table.
- Both required commands are green: `mvn test` (120 tests, Docker-free) and `mvn verify -Pintegration -Dit.test=PostgresMigrationIT` (6/6, real Postgres).
- HARD-02 is NOT marked complete — it spans both plans in this phase and closes only when plan 02-02's exhaustive V9 merge assertions land.

---
*Phase: 02-postgres-migration-integration-tests*
*Completed: 2026-07-13*

## Self-Check: PASSED

- FOUND: pom.xml
- FOUND: src/test/java/com/onestopsports/migration/PostgresMigrationIT.java
- FOUND: commit 73f376b (Task 1)
- FOUND: commit 1a73f88 (Task 2)
- FOUND: .planning/phases/02-postgres-migration-integration-tests/02-01-SUMMARY.md
