---
phase: 02-postgres-migration-integration-tests
reviewed: 2026-07-13T17:36:07Z
depth: standard
files_reviewed: 4
files_reviewed_list:
  - src/test/java/com/onestopsports/migration/PostgresMigrationIT.java
  - pom.xml
  - CLAUDE.md
  - README.md
findings:
  critical: 0
  warning: 0
  info: 3
  total: 3
status: issues_found
---

# Phase 02: Code Review Report

**Reviewed:** 2026-07-13T17:36:07Z
**Depth:** standard
**Files Reviewed:** 4
**Status:** issues_found (info-only — no correctness or security defects found)

## Summary

This phase adds `PostgresMigrationIT`, a Testcontainers-backed integration test that runs the real V1→V9 Flyway chain against an ephemeral `postgres:16-alpine` container, plus two `org.testcontainers` test-scoped deps and an opt-in `integration` Maven profile.

I traced every assertion in `PostgresMigrationIT` against the literal SQL in `V8__add_name_normalized.sql` and `V9__team_league_many_to_many.sql` (not just against the test's own comments), and independently verified the Maven claims empirically rather than trusting the inline comments:

- `mvn dependency:tree -Dincludes=org.testcontainers` confirms both `org.testcontainers:postgresql` and `org.testcontainers:junit-jupiter` resolve to `1.20.6` with no explicit `<version>` in the POM — genuinely managed by the `spring-boot-starter-parent:3.4.4` BOM, and both are `test`-scoped.
- `mvn help:effective-pom` confirms `maven-failsafe-plugin` is only present under the parent's `<pluginManagement>` (version/goal defaults only) and is never added to the default build's `<plugins>` — it's only activated inside the `integration` profile. This matches the pom.xml comment's claim.
- `mvn test-compile` succeeds and `mvn surefire:test -Dtest='com.onestopsports.migration.*'` reports "No tests matching pattern... were executed", empirically confirming Surefire's default `*Test.java`/`*Tests.java` patterns never pick up `PostgresMigrationIT.java` (named `*IT.java`) — `mvn test` genuinely never requires Docker.
- Every fixture INSERT and every assertion query uses `JdbcTemplate` `?` placeholders — no string concatenation anywhere in the file. SQL-injection-in-test-code requirement is satisfied.
- Walked the V9 merge SQL step-by-step (team_merge_map re-point → team_league re-point/ON CONFLICT DO NOTHING → favourite re-point with NOT EXISTS guard → delete → player_merge_map dedup) against each of the 7 exhaustive merge assertions (`v9_duplicateClubs_mergedIntoCanonicalRow` through `v9_favoritePlayer_dupDeleted_whenCanonicalAlreadyFavorited`) and confirmed each assertion proves a real, non-tautological outcome of the actual merge logic (canonical = MIN(id) survivor, duplicate deleted, collision branch correctly skips re-point via the NOT EXISTS guard and lets the trailing DELETE clean up the leftover row, repoint branch correctly re-points). All IDs used in assertions are captured dynamically after INSERT (via `queryForObject`) — no hardcoded IDs, so no ID-collision flakiness.
- Docs (CLAUDE.md/README) accurately describe the two-stage Flyway target("8")/target("9") pattern, the opt-in `mvn verify -Pintegration` invocation, the 13-test breakdown (6 schema-shape + 7 exhaustive merge), and the `postgres:16-alpine` version match with `docker-compose.yml` (verified against the actual `docker-compose.yml` image tag). No inaccuracies found.

No BLOCKER or WARNING-level defects were found. The three items below are minor, non-blocking quality observations.

## Info

### IN-01: Exception-cause assertions couple test correctness to Spring's exception-translation internals

**File:** `src/test/java/com/onestopsports/migration/PostgresMigrationIT.java:477-484` and `:518-525`
**Issue:** Both collision tests assert the unique-violation shape via `.extracting(Throwable::getCause).isInstanceOf(SQLException.class).extracting(cause -> ((SQLException) cause).getSQLState()).isEqualTo("23505")`. This is correct today — `SQLErrorCodeSQLExceptionTranslator` sets the original `SQLException`/`PSQLException` as the direct cause of `DataIntegrityViolationException` for a single, un-batched `PreparedStatement` failure — but it relies on `getCause()` being exactly one level deep, which is an implementation detail of Spring's exception translation rather than a contract Spring guarantees. A future Spring Framework change to exception wrapping depth (e.g. via `UncategorizedSQLException`) would silently break this assertion's `getCause()` chain rather than the underlying behavior it's meant to prove.
**Fix:** Not urgent, but consider asserting via `org.springframework.dao.DataAccessException`'s own `getMostSpecificCause()` instead of a hardcoded single `getCause()` hop, which is more resilient to translation-depth changes:
```java
assertThatThrownBy(() -> jdbc.update(
        "INSERT INTO favorite_team (user_id, team_id) VALUES (?, ?)",
        userTeamCollisionId, canonicalTeamId))
        .isInstanceOf(DataIntegrityViolationException.class)
        .extracting(t -> ((DataIntegrityViolationException) t).getMostSpecificCause())
        .isInstanceOfSatisfying(SQLException.class,
                cause -> assertThat(cause.getSQLState()).isEqualTo("23505"));
```

### IN-02: `seedFixtures` mixes two unrelated fixture scenarios in one ~120-line method

**File:** `src/test/java/com/onestopsports/migration/PostgresMigrationIT.java:138-261`
**Issue:** `seedFixtures` seeds both the plan-02-01 minimal (non-duplicate) V8 fixture and the plan-02-02 exhaustive duplicate-club merge fixture in a single static method. The two scenarios are logically independent (different fields, different `@Test` methods consume them) and the method's own header comment already documents them as "1." and "2." — a signal that they'd read more clearly as two separate `seedSchemaShapeFixture(jdbc)` / `seedDuplicateClubFixture(jdbc)` helper methods called from `migrateAndSeed()`.
**Fix:** Split into two named methods, e.g.:
```java
private static void seedSchemaShapeFixture(JdbcTemplate jdbc, Long sportId) { ... } // lines 138-171
private static void seedDuplicateClubFixture(JdbcTemplate jdbc, Long sportId) { ... } // lines 172-261
```

### IN-03: Near-identical copy/paste between the team-level and player-level collision tests

**File:** `src/test/java/com/onestopsports/migration/PostgresMigrationIT.java:459-485` (`v9_favoriteTeam_dupDeleted_whenCanonicalAlreadyFavorited`) and `:503-526` (`v9_favoritePlayer_dupDeleted_whenCanonicalAlreadyFavorited`)
**Issue:** These two test methods are structurally identical (query surviving rows → assert single canonical survivor → attempt a duplicate INSERT → assert `DataIntegrityViolationException`/SQLState `23505`), differing only in table/column names. This is reasonable given each test also documents itself independently for a junior-developer reader, but it is still literal code duplication per the review's code-quality scope.
**Fix:** Optional — extract a small parameterized helper (e.g. `assertUniqueConstraintStillHolds(String insertSql, Object... args)`) shared by both tests, or leave as-is if the team's convention favors self-contained, independently-readable test methods over DRY test helpers (a defensible tradeoff for integration tests).

---

_Reviewed: 2026-07-13T17:36:07Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
