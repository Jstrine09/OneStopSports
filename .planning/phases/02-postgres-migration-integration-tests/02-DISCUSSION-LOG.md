# Phase 2: Postgres Migration Integration Tests - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-07-13
**Phase:** 2-Postgres Migration Integration Tests
**Areas discussed:** Postgres harness, Test gating, V9 merge staging, Assertion depth

---

## Postgres Harness

| Option | Description | Selected |
|--------|-------------|----------|
| Testcontainers | Ephemeral `postgres:16` container per run; industry standard for Flyway ITs; matches docker-compose version; one new test dep (PG driver + flyway-database-postgresql already present); Docker only at IT time | ✓ |
| Dockerless embedded (zonky) | Real PG binary, no Docker daemon; lighter env reqs but less standard; still a new dep | |
| Reuse docker-compose PG | Point IT at existing onestopsports PG; no new dep but shared/stateful and needs compose up; risky for a clean-slate migration test | |

**User's choice:** Testcontainers
**Notes:** Clean-slate per run and the standard tool; only Testcontainers itself needs adding to `pom.xml`.

---

## Test Gating

| Option | Description | Selected |
|--------|-------------|----------|
| Opt-in profile / tag | JUnit `@Tag` / `*IT` naming + Maven profile (e.g. `mvn verify -Pintegration`); default `mvn test` stays H2-only and Docker-free; documented so it can be wired into CI | ✓ |
| Default mvn test | Runs every build for max regression safety, but every dev/CI run then needs Docker and a Docker-less env breaks the build | |

**User's choice:** Opt-in profile / tag
**Notes:** Must be clearly documented (README/CLAUDE.md) so it's run deliberately and can join CI. Keeps the existing 120-test H2 suite Docker-free.

---

## V9 Merge Staging

| Option | Description | Selected |
|--------|-------------|----------|
| Migrate to V8, JDBC-seed dupes, then run V9 | `flyway.target=8` → insert duplicate clubs/players/links/favourites via JDBC/SQL in the test → run V9 → assert; fixture in test code, precise, no prod-migration pollution | ✓ |
| Extra Flyway test-seed migration before V9 | Test-only seed on a test migration path ordered before V9; declarative but fragile on version ordering and risks leaking into the prod location | |

**User's choice:** Migrate to V8, JDBC-seed dupes, then run V9
**Notes:** Keeps the duplicate-scenario fixture visible in the test and out of the production migration path.

---

## Assertion Depth

| Option | Description | Selected |
|--------|-------------|----------|
| Exhaustive V9 outcomes | Dupes merged to canonical row, `team_league` populated, players + league-links + favourites re-pointed (respecting unique constraints), `team.league_id` dropped, `sport_id` backfilled + V8 basics | ✓ |
| Focused highest-risk subset | Club merge + `team_league` + `league_id` dropped + V8 basics; skip favourites/league-link re-pointing + unique-constraint detail | |

**User's choice:** Exhaustive V9 outcomes
**Notes:** The favourites de-dupe + unique-constraint re-pointing is the subtlest, highest-bug-risk part of the merge, so it is explicitly in scope.

---

## Claude's Discretion

- Test class/package name and file layout; new test profile filename (e.g. `application-it.yml`); Testcontainers wiring style (`@Container` + `@ServiceConnection` vs manual datasource override); Maven gating mechanism (Failsafe vs tag-filtered Surefire); JDBC vs `JdbcTemplate` for seeding/asserting. All new Java carries junior-developer inline comments (project hard rule).

## Deferred Ideas

None — discussion stayed within phase scope.
