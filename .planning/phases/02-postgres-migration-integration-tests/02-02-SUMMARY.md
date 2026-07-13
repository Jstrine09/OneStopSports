---
phase: 02-postgres-migration-integration-tests
plan: 02
subsystem: testing
tags: [testcontainers, flyway, postgres, junit5, maven, integration-test, v9-merge]

# Dependency graph
requires:
  - phase: 02-postgres-migration-integration-tests
    plan: 01
    provides: "PostgresMigrationIT harness (Testcontainers postgres:16-alpine, two-stage target(8)->seedFixtures->target(9) Flyway migration, opt-in mvn verify -Pintegration profile) and its seedFixtures method, extended by this plan"
provides:
  - "Exhaustive V9 data-merge verification: duplicate-club merge, team_league population across both competitions, player re-point + de-dup, and favorite_team/favorite_player re-point + collision-skip (proven behaviorally via SQLState 23505)"
  - "Documented mvn verify -Pintegration command + Docker prerequisite in CLAUDE.md and README.md"
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Duplicate-club JDBC fixture built at the V8-era schema point (team.league_id still exists, team.sport_id does not) so V9 itself backfills sport_id and performs the merge, matching real pre-V9 production data shape"
    - "Behavioral unique-constraint proof: attempt a duplicate INSERT and assert SQLState 23505 via AssertJ's assertThatThrownBy + DataIntegrityViolationException.getCause(), instead of hardcoding Postgres's auto-generated inline-UNIQUE constraint name"

key-files:
  created: []
  modified:
    - src/test/java/com/onestopsports/migration/PostgresMigrationIT.java
    - CLAUDE.md
    - README.md

key-decisions:
  - "Reused the sportId created by 02-01's fixture (querying it by slug 'football') rather than seeding a second sport row, since V9's merge grouping only requires the two duplicate leagues to share sport_id — this keeps the fixture additive to 02-01's existing rows rather than duplicating the sport seed."
  - "Used 4 distinct users (user_team_repoint, user_team_collision, user_player_repoint, user_player_collision) rather than reusing 2 users across both team and player branches, for clearer per-branch test readability even though favorite_team/favorite_player are independent tables and reuse would have worked."
  - "Both collision-skip tests prove the surviving favourite is unique BEHAVIORALLY (attempt a duplicate INSERT, assert AssertJ catches DataIntegrityViolationException whose cause is a SQLException with SQLState 23505) rather than asserting against a guessed Postgres auto-generated constraint name, per RESEARCH.md Pitfall 6."
  - "Documented the local Docker Desktop MinAPIVersion workaround (-DargLine=\"-Dapi.version=1.41\") discovered in 02-01 as a CLAUDE.md troubleshooting note, while keeping the plain `mvn verify -Pintegration` as the primary documented command (works unmodified on standard Docker / CI)."

requirements-completed: []  # HARD-02 spans both plans in this phase; closed at phase completion, not by this plan per its explicit instruction

coverage:
  - id: D1
    description: "Duplicate clubs sharing (sport_id, external_id) collapse into ONE canonical row (MIN(id)); the duplicate is deleted"
    requirement: "HARD-02"
    verification:
      - kind: integration
        ref: "PostgresMigrationIT#v9_duplicateClubs_mergedIntoCanonicalRow"
        status: pass
    human_judgment: false
  - id: D2
    description: "team_league is populated for the canonical club across BOTH competitions; no team_league row references the deleted duplicate"
    requirement: "HARD-02"
    verification:
      - kind: integration
        ref: "PostgresMigrationIT#v9_teamLeagueJoinTable_populatedForCanonical"
        status: pass
    human_judgment: false
  - id: D3
    description: "Duplicate players re-pointed onto the canonical team and de-duplicated by (team_id, name), keeping the MIN(id) row"
    requirement: "HARD-02"
    verification:
      - kind: integration
        ref: "PostgresMigrationIT#v9_duplicatePlayers_repointedAndDeduplicated"
        status: pass
    human_judgment: false
  - id: D4
    description: "favorite_team re-pointed to canonical (no-collision branch) AND collision-skip deletes the duplicate-pointing row, proven behaviorally via SQLState 23505"
    requirement: "HARD-02"
    verification:
      - kind: integration
        ref: "PostgresMigrationIT#v9_favoriteTeam_repointedToCanonical_whenNoCollision, #v9_favoriteTeam_dupDeleted_whenCanonicalAlreadyFavorited"
        status: pass
    human_judgment: false
  - id: D5
    description: "favorite_player mirrors the same re-point + collision-skip behavior at the player level"
    requirement: "HARD-02"
    verification:
      - kind: integration
        ref: "PostgresMigrationIT#v9_favoritePlayer_repointedToCanonical_whenNoCollision, #v9_favoritePlayer_dupDeleted_whenCanonicalAlreadyFavorited"
        status: pass
    human_judgment: false
  - id: D6
    description: "mvn verify -Pintegration + Docker prerequisite documented in CLAUDE.md and README.md"
    requirement: "HARD-02"
    verification:
      - kind: unit
        ref: "grep -q \"mvn verify -Pintegration\" CLAUDE.md README.md -> DOCS_OK"
        status: pass
    human_judgment: false
  - id: D7
    description: "mvn test stays green (120 H2 tests) and Docker-free after adding the merge fixtures/assertions"
    requirement: "HARD-02"
    verification:
      - kind: unit
        ref: "mvn -q test (17 test classes, 120 tests)"
        status: pass
    human_judgment: false

duration: 16min
completed: 2026-07-13
status: complete
---

# Phase 2 Plan 2: Exhaustive V9 Data-Merge Verification Summary

**Extended `PostgresMigrationIT`'s `seedFixtures` with a duplicate-club scenario (two team rows sharing `external_id`, duplicate players, four users covering the re-point vs collision favourite branches) and added seven `@Test` methods that exhaustively prove V9's merge SQL — club collapse to canonical `MIN(id)`, `team_league` population across both competitions, player re-point + de-dup, and `favorite_team`/`favorite_player` re-point + collision-skip proven behaviorally via a SQLState `23505` unique-violation check — completing HARD-02's data-merge coverage; all 13 IT tests (6 from 02-01 + 7 new) pass against real Postgres, and the default `mvn test` stays green at 120 tests, Docker-free.**

## Performance

- **Duration:** 16 min
- **Started:** 2026-07-13T12:58:30-04:00 (02-01 completion)
- **Completed:** 2026-07-13T13:14:39-04:00
- **Tasks:** 3/3
- **Files modified:** 3 (0 created, 3 modified)

## Accomplishments
- Extended `seedFixtures` with the full duplicate-club scenario from RESEARCH.md's Pattern 2: two leagues sharing a sport (Domestic League + Continental Cup), two team rows sharing `external_id = '86'` linked to different leagues, a duplicate player under both team rows, and four users whose favourites exercise both the re-point branch (favourite only the duplicate) and the collision/de-dupe branch (favourite both canonical and duplicate) for teams and players.
- Added `insertUser` helper and seven new `@Test` methods asserting every documented V9 merge guarantee against the permanent `team`/`player`/`team_league`/`favorite_team`/`favorite_player` tables (never V9's session-scoped temp tables).
- Proved the favourites collision-skip behaviorally: attempting a duplicate `(user_id, team_id)`/`(user_id, player_id)` INSERT after the merge throws `DataIntegrityViolationException` whose cause carries Postgres SQLState `23505` — no hardcoded, guessed constraint name.
- `mvn verify -Pintegration -Dit.test=PostgresMigrationIT -DargLine="-Dapi.version=1.41"`: 13/13 tests pass (`BUILD SUCCESS`). `mvn -q test` (no profile): 120 tests pass, 0 Docker required (Surefire never sees `*IT.java`).
- Documented `mvn verify -Pintegration` + Docker daemon prerequisite (and the local Docker Desktop `-DargLine="-Dapi.version=1.41"` workaround) in CLAUDE.md's Testing section, updated the two stale "V8/V9 unit-tested only by compile/entity-mapping" caveats now that the real-Postgres IT covers them, and added a one-line testing pointer to README.md.

## Task Commits

Each task was committed atomically:

1. **Task 1: Extend seedFixtures with the duplicate-club scenario exercising every V9 merge branch** - `cb645c0` (test)
2. **Task 2: Add the exhaustive V9 merge assertions incl. favourites collision-skip** - `80c9b07` (test)
3. **Task 3: Document the opt-in `mvn verify -Pintegration` command + Docker prerequisite** - `e4999a0` (docs)

**Plan metadata:** (this commit, following SUMMARY.md)

## Files Created/Modified
- `src/test/java/com/onestopsports/migration/PostgresMigrationIT.java` - `seedFixtures` extended with the duplicate-club scenario (leagues, teams, players, 4 users + favourites) plus a new `insertUser` helper and seven `@Test` methods asserting the full V9 merge contract.
- `CLAUDE.md` - Testing section documents `mvn verify -Pintegration` + Docker prerequisite + the local Docker Desktop `-Dapi.version` troubleshooting note; the two stale V8/V9 coverage-gap caveats updated to reflect the closed gap.
- `README.md` - one-line pointer to `mvn test` and `mvn verify -Pintegration`.

## Decisions Made
- Reused the `sportId` created by 02-01's own fixture (queried by slug) instead of seeding a second sport row — keeps this plan's fixture strictly additive.
- Used 4 distinct users (one per branch × team/player) rather than reusing 2 users across both favourite tables, for clearer per-test readability.
- Collision-skip proof is behavioral (SQLState `23505` via `assertThatThrownBy` + `DataIntegrityViolationException`), never a hardcoded/guessed Postgres auto-generated constraint name, per RESEARCH.md Pitfall 6.
- Kept `mvn verify -Pintegration` (unmodified) as the primary documented command; the local Docker Desktop `MinAPIVersion` workaround is documented as a troubleshooting note, not baked into `pom.xml`, per the environment constraints inherited from 02-01.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required. Running the integration suite requires a local Docker daemon (confirmed available and used successfully in this session).

## Next Phase Readiness
- Both required commands are green: `mvn test` (120 tests, Docker-free) and `mvn verify -Pintegration -Dit.test=PostgresMigrationIT -DargLine="-Dapi.version=1.41"` (13/13, real Postgres).
- HARD-02 is NOT marked complete by this plan — per the plan's explicit instruction, it closes at phase completion (the orchestrator handles this).
- All four roadmap Success Criteria for Phase 2 are now technically satisfied by the combination of 02-01 + 02-02: (1) full V1→V9 chain runs against real Postgres, (2) V8 `name_normalized` columns/indexes/backfill proven, (3) V9 data merge (club/player/favourites) proven exhaustively, (4) `mvn verify -Pintegration` + Docker prerequisite documented.

---
*Phase: 02-postgres-migration-integration-tests*
*Completed: 2026-07-13*

## Self-Check: PASSED

- FOUND: src/test/java/com/onestopsports/migration/PostgresMigrationIT.java
- FOUND: CLAUDE.md
- FOUND: README.md
- FOUND: .planning/phases/02-postgres-migration-integration-tests/02-02-SUMMARY.md
- FOUND: commit cb645c0 (Task 1)
- FOUND: commit 80c9b07 (Task 2)
- FOUND: commit e4999a0 (Task 3)
