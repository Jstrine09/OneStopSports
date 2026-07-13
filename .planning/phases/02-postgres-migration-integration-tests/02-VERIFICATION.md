---
phase: 02-postgres-migration-integration-tests
verified: 2026-07-13T13:35:00-04:00
status: passed
score: 4/4 must-haves verified
behavior_unverified: 0
overrides_applied: 0
---

# Phase 2: Postgres Migration Integration Tests Verification Report

**Phase Goal:** The two Flyway migrations that only ever run against real Postgres (V8 name_normalized, V9 team↔league M:N data merge) are verified by an integration test against a real Postgres instance, so their schema changes and one-time data merges are proven correct rather than only compile-checked.

**Verified:** 2026-07-13T13:35:00-04:00
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth (Roadmap Success Criterion) | Status | Evidence |
|---|---|---|---|
| 1 | An integration test runs the full V1→V9 Flyway migration chain against a real Postgres instance (Testcontainers), separate from the H2 unit-test profile | ✓ VERIFIED | `PostgresMigrationIT` (`src/test/java/com/onestopsports/migration/PostgresMigrationIT.java`) is a plain-JUnit5 class, NOT `@SpringBootTest`, annotated `@Testcontainers(disabledWithoutDocker = true)` with `@Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")`. `@BeforeAll` runs Flyway `.locations("classpath:db/migration").target("8")` then, after `seedFixtures`, `.target("9")` against the same container's JDBC URL. Independently re-ran `mvn verify -Pintegration -Dit.test=PostgresMigrationIT -DargLine="-Dapi.version=1.41"` in this session with Docker running — real `postgres:16-alpine` container booted via Testcontainers, log shows Flyway applying migrations 1 through 9 sequentially against `jdbc:postgresql://localhost:.../test`, `Tests run: 13, Failures: 0, Errors: 0`, `BUILD SUCCESS`. Separate from H2: `application-test.yml` unchanged (git diff empty), H2 profile still has Flyway off. |
| 2 | Asserts V8 outcomes: `team.name_normalized` + `player.name_normalized` columns exist, are indexed, and are backfilled accent-stripped/lower-cased for seeded rows | ✓ VERIFIED | `v8_nameNormalizedColumns_existAndAreIndexed` queries `information_schema.columns` for both columns and `pg_indexes` for `idx_team_name_normalized`/`idx_player_name_normalized` — matches V8's literal SQL identifiers. `v8_nameNormalized_roundTripsTextNormalizerOutput` asserts the seeded "Atlético Madrid" row's stored `name_normalized` equals `TextNormalizer.normalize("Atlético Madrid")` AND the literal string `"atletico madrid"` — verified `TextNormalizer.normalize` (NFD decompose + strip combining marks + lowercase) produces exactly that. Both tests passed in the re-run (13/13 IT green). |
| 3 | Asserts V9 outcomes: duplicate clubs sharing `(sport_id, external_id)` merged into one canonical row, `team_league` join populated, players/league-links/favourites re-pointed onto canonical rows, `team.league_id` dropped | ✓ VERIFIED | Exhaustive coverage confirmed by reading both the test file and V9's SQL side-by-side: `v9_duplicateClubs_mergedIntoCanonicalRow` (MIN(id) survivor, duplicate gone), `v9_teamLeagueJoinTable_populatedForCanonical` (canonical linked to BOTH leagues, no orphaned link to deleted duplicate), `v9_duplicatePlayers_repointedAndDeduplicated` (one player row per (team_id,name), canonical id survives), `v9_favoriteTeam_repointedToCanonical_whenNoCollision` + `v9_favoriteTeam_dupDeleted_whenCanonicalAlreadyFavorited` (no-collision re-point AND collision-skip, the latter proven **behaviorally** via `assertThatThrownBy` on a duplicate INSERT asserting `DataIntegrityViolationException` → cause `SQLException` → `getSQLState()=="23505"`, no hardcoded constraint name), `v9_favoritePlayer_repointedToCanonical_whenNoCollision` + `v9_favoritePlayer_dupDeleted_whenCanonicalAlreadyFavorited` (mirror at player level, same 23505 behavioral proof), `v9_teamLeagueIdColumn_isDropped` (0 rows in `information_schema.columns` for `team.league_id`), `v9_teamSportId_isNotNull_andForeignKeyed` (`is_nullable='NO'` + `fk_team_sport` constraint present — this name IS literal in V9's SQL, safe to assert directly). The fixture (`seedFixtures`) plants the duplicate-club scenario at the exact V8-era point (two teams sharing `external_id='86'` under two leagues sharing one sport, duplicate player under both, 4 users covering re-point vs collision branches for both favorite_team and favorite_player) — matches V9's merge logic (`team_merge_map`/`player_merge_map` CTEs, `NOT EXISTS` guard + trailing DELETE) verified line-for-line against `V9__team_league_many_to_many.sql`. All 7 merge assertions passed in the re-run. |
| 4 | The migration IT runs in `mvn test` OR a clearly-documented `mvn` profile, green, without breaking the existing H2-based suite | ✓ VERIFIED | `pom.xml` declares `org.testcontainers:postgresql` + `org.testcontainers:junit-jupiter` (test scope, no `<version>`, managed by `spring-boot-starter-parent:3.4.4`) and a sibling `<profiles><profile id="integration">` wrapping a bare `maven-failsafe-plugin` (no version, no configuration — relies on Failsafe's default `**/*IT.java` pattern and pre-bound executions). The existing `<build>` block is untouched. Independently re-ran `mvn test` (no profile) in this session: `Tests run: 120, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`, ~1m14s, no Docker daemon invoked (Surefire never matched `PostgresMigrationIT.java`). Independently re-ran `mvn verify -Pintegration -Dit.test=PostgresMigrationIT -DargLine="-Dapi.version=1.41"`: `Tests run: 13, Failures: 0, Errors: 0`, `BUILD SUCCESS`, real Testcontainers Postgres container observed in logs. Documentation: `CLAUDE.md` line 200 documents `mvn verify -Pintegration`, names `PostgresMigrationIT`, states the Docker prerequisite and the local Docker Desktop `-Dapi.version=1.41` workaround; `README.md` line 4 points at both `mvn test` and `mvn verify -Pintegration`. |

**Score:** 4/4 truths verified (0 present-but-behavior-unverified)

### Required Artifacts

| Artifact | Expected | Status | Details |
|---|---|---|---|
| `pom.xml` | Testcontainers deps (version-less) + opt-in `integration` profile wrapping bare `maven-failsafe-plugin` | ✓ VERIFIED | Confirmed via direct read: lines 128-144 (deps, both `<scope>test</scope>`, no `<version>`), lines 189-215 (`<profiles>` sibling of `<build>`, profile id `integration`, bare failsafe plugin, no version/configuration). Existing `<build>` block byte-identical to before (spring-boot-maven-plugin + maven-compiler-plugin unchanged). |
| `src/test/java/com/onestopsports/migration/PostgresMigrationIT.java` | Plain-JUnit5 Testcontainers IT with V8+V9 assertions | ✓ VERIFIED | 527 lines, package `com.onestopsports.migration`, no `@SpringBootTest`, `@Testcontainers(disabledWithoutDocker=true)`, 13 `@Test` methods (6 from plan 02-01 + 7 from plan 02-02), all passing against real Postgres. |
| `CLAUDE.md` | Documents `mvn verify -Pintegration` + Docker prerequisite; updates stale HARD-02 coverage-gap caveat | ✓ VERIFIED | Line 200 (Testing section), line 227 (coverage-gap note updated to reflect closure), line 253 (Known-issues note documents the closure). |
| `README.md` | Points at both test commands | ✓ VERIFIED | Line 4: `mvn test` (fast H2) + `mvn verify -Pintegration` (Docker-required Postgres migration IT). |

### Key Link Verification

| From | To | Via | Status | Details |
|---|---|---|---|---|
| `PostgresMigrationIT` | real Postgres (Testcontainers) | `postgres.getJdbcUrl()/getUsername()/getPassword()` fed into `DriverManagerDataSource` + `Flyway.configure().dataSource(...)` | ✓ WIRED | No `application-*.yml`/env var is read anywhere in the class; confirmed by direct read of the full file — only the container-provided credentials are used, satisfying the "reads its datasource only from the Testcontainers container" instruction. |
| `PostgresMigrationIT` | production migration folder | `.locations("classpath:db/migration")` (both Stage 1 and Stage 2) | ✓ WIRED | Same literal path used twice — no duplicated/stale test migration folder exists in the repo; confirmed no `src/test/resources/db/migration` directory was created. |
| Test identifiers (constraint/column/index names) | real V8/V9 SQL | Literal string match | ✓ WIRED | `idx_team_name_normalized`, `idx_player_name_normalized`, `fk_team_sport`, `team_league`, column names `name_normalized`/`sport_id`/`league_id` — every identifier asserted in the test was cross-checked character-for-character against `V8__add_name_normalized.sql` and `V9__team_league_many_to_many.sql`; all match exactly. |
| Surefire (`mvn test`) | `PostgresMigrationIT` | Class-name suffix gate | ✓ WIRED (negative link, correctly NOT triggered) | Confirmed by live re-run: `mvn test` (no profile) shows exactly the pre-existing 17 test classes / 120 tests, `PostgresMigrationIT` never appears in Surefire's run list, and no Docker daemon activity was observed. |
| Failsafe (`mvn verify -Pintegration`) | `PostgresMigrationIT` | Default `**/*IT.java` pattern, opt-in profile | ✓ WIRED | Confirmed by live re-run: Failsafe picked up and ran `com.onestopsports.migration.PostgresMigrationIT`, 13/13 green. |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|---|---|---|---|---|
| HARD-02 | 02-01, 02-02 | Real-Postgres integration test verifying V8 + V9 migrations | ✓ SATISFIED (implementation) | All four roadmap success criteria independently confirmed above. Note: `REQUIREMENTS.md` line 15 still shows `[ ]` (unchecked) for HARD-02 — this is expected and intentional per both plans' explicit frontmatter (`requirements-completed: []` with the comment "HARD-02 spans both plans; not marked complete until phase completion" / "closed at phase completion, not by this plan"). This is an orchestrator bookkeeping step that occurs after verification passes, not a gap in the delivered work. |

No orphaned requirements — `HARD-02` is the only requirement mapped to Phase 2 in `REQUIREMENTS.md`'s traceability table, and both plans declare it in frontmatter.

### Anti-Patterns Found

None. Scanned `PostgresMigrationIT.java`, `pom.xml` diff, `V8`/`V9` SQL (unmodified), `CLAUDE.md`/`README.md` diffs for `TODO`/`FIXME`/`HACK`/`PLACEHOLDER`/`TBD`/`XXX`/empty-return stubs — no matches. All SQL in the test (fixture inserts and assertion queries) uses parameterized JdbcTemplate `?` placeholders; no string concatenation/`String.format` SQL found (satisfies the plan's own STRIDE tampering mitigation). No hardcoded Postgres auto-generated constraint names — the two collision-skip tests correctly use behavioral SQLState `23505` proof instead of guessing a constraint name (per RESEARCH.md Pitfall 6, explicitly followed).

### Behavioral Spot-Checks / Independent Re-Runs

| Behavior | Command | Result | Status |
|---|---|---|---|
| Default suite stays green, Docker-free | `mvn test` | `Tests run: 120, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS` (~1m14s) | ✓ PASS |
| Real-Postgres migration IT is green | `mvn verify -Pintegration -Dit.test=PostgresMigrationIT -DargLine="-Dapi.version=1.41"` | `Tests run: 13, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS`; Flyway log shows sequential migration 1→9 against a real Testcontainers `postgres:16-alpine` container | ✓ PASS |

Both commands were re-run independently in this verification session (Docker was confirmed running via `docker info`) rather than trusting SUMMARY.md's reported results — actual output matches the SUMMARY.md claims exactly (13/13, 120/120).

### Human Verification Required

None. All roadmap success criteria are mechanically verifiable (schema/catalog queries, migration chain execution, behavioral SQLState proof) and were independently re-executed.

## Gaps Summary

No gaps. All four roadmap Success Criteria are met:
1. Full V1→V9 chain runs against real Postgres via Testcontainers, separate from the H2 profile — verified by direct re-run.
2. V8 outcomes (columns, indexes, accent-stripped/lower-cased backfill round-trip) are asserted and pass.
3. V9 outcomes (duplicate-club merge to canonical MIN(id), team_league re-point across both competitions, player de-dup by (team_id,name), favourite re-point on no-collision AND behaviorally-proven collision-skip via SQLState 23505 for both favorite_team and favorite_player, team.league_id dropped) are asserted and pass — the exhaustive V9 coverage explicitly requested in the verification instructions is present and correct.
4. The IT runs via a clearly documented opt-in `mvn verify -Pintegration` profile; the default `mvn test` is unaffected (still 120 tests, Docker-free); documentation exists in both CLAUDE.md and README.md.

The one non-blocking observation is that `REQUIREMENTS.md`'s HARD-02 checkbox remains unchecked — this is by design per the plans' own frontmatter (deferred to phase-completion bookkeeping) and not a functional gap.

---

_Verified: 2026-07-13T13:35:00-04:00_
_Verifier: Claude (gsd-verifier)_
