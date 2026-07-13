# Phase 2: Postgres Migration Integration Tests - Research

**Researched:** 2026-07-13
**Domain:** Spring Boot 3.4.4 / Java 21 / Flyway 10.20.1 / Testcontainers real-Postgres integration testing
**Confidence:** HIGH

## Summary

This phase adds exactly one new artifact class of work: a JUnit 5 integration test that spins up an ephemeral `postgres:16-alpine` Testcontainers instance, runs the real V1→V9 Flyway chain against it (in two stages: target `8`, then target `9`), and asserts both migrations' documented guarantees via raw SQL/JDBC. Everything needed is already resolvable through Spring Boot 3.4.4's own dependency management — **no BOM import and no explicit version pins are required** for Testcontainers, Flyway, or the Maven Failsafe plugin; they are all already managed transitively by `spring-boot-starter-parent:3.4.4`, confirmed by direct inspection of the official POMs on Maven Central. Two `<dependency>` blocks (`org.testcontainers:postgresql`, `org.testcontainers:junit-jupiter`) and a small Maven profile are the only `pom.xml` changes needed.

The cleanest way to satisfy all four locked decisions (D-01…D-04) simultaneously is a **plain JUnit 5 test class with no Spring context at all** — `@Testcontainers` + a static `PostgreSQLContainer<?>`, the raw `org.flywaydb.core.Flyway` Java API called twice with different `.target(...)` values, and a hand-built `JdbcTemplate` (Spring's `spring-jdbc` is already on the classpath) for both seeding fixtures and asserting outcomes. This avoids Spring Boot's single-shot auto-Flyway (which cannot pause mid-chain to let the test inject fixtures between V8 and V9), avoids the app's other `ApplicationRunner`s (`DataLoader`, `NbaDataLoader`, `NflDataLoader`) firing and touching live external APIs, and is materially faster (no context boot). It is explicitly sanctioned by CONTEXT.md's `<code_context>` section ("a slice/@JdbcTest-style or plain Flyway+DataSource test is also viable (planner's call)").

Gating (D-02) has an unusually clean answer for this codebase: **Maven Failsafe's own default naming convention (`**/*IT.java`) is already excluded from Surefire's default `mvn test` inclusion pattern, and `maven-failsafe-plugin` is already plugin-managed by `spring-boot-starter-parent` with its `integration-test`+`verify` goal executions pre-bound** — it is simply not declared in this project's `<build><plugins>` yet. Naming the new test class `*IT.java` and adding the plugin declaration **only inside a new Maven profile** (e.g. `integration`) means `mvn test` never sees it (Surefire's default include patterns are `**/*Test.java`, `**/*Tests.java`, `**/*TestCase.java`, `**/Test*.java` — never `*IT.java`) and `mvn verify` without the profile never loads Failsafe at all. No JUnit 5 `@Tag` is strictly required, though one can be added as a defense-in-depth Surefire exclude if desired.

**Primary recommendation:** Plain JUnit 5 (no `@SpringBootTest`) + `@Testcontainers` static `PostgreSQLContainer<?>("postgres:16-alpine")` + raw `Flyway.configure().target("8").load().migrate()` → JDBC-seed duplicate-club fixtures via `JdbcTemplate` → `Flyway.configure().target("9").load().migrate()` → assert via `JdbcTemplate`/`information_schema`/`pg_indexes`. Gate with Failsafe's `*IT.java` convention wrapped in a new `integration` Maven profile, invoked via `mvn verify -Pintegration`.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Ephemeral Postgres provisioning | Test infrastructure (Testcontainers) | — | Container lifecycle is test-only; no production tier owns it |
| Staged schema migration (V1→V8, seed, V9) | Test code (raw Flyway API) | Database / Storage | The migration logic itself lives in `src/main/resources/db/migration` (Database tier); the *staging/orchestration* of running it in two passes is test-only and must not leak into `src/main` |
| Fixture seeding (duplicate clubs/players/favourites) | Test code (JDBC/`JdbcTemplate`) | — | Deliberately bypasses the JPA/Hibernate tier (`@PrePersist` hooks) to keep the fixture hermetic and independent of `DataLoader` |
| Outcome assertions (columns, indexes, row counts, re-pointing) | Test code (JDBC/`JdbcTemplate`) | Database / Storage | Assertions read the real Postgres catalog (`information_schema`, `pg_indexes`) — this is the one place raw SQL introspection is the correct tool, not JPA |
| Build/CI gating (opt-in execution) | Build tooling (Maven Failsafe + profile) | — | Execution-phase concern, not application code |

## User Constraints

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Postgres Harness**
- **D-01:** Use **Testcontainers** (`org.testcontainers:postgresql`) to spin up an ephemeral `postgres:16` container per integration-test run — matching the `postgres:16-alpine` version already used in `docker-compose.yml`. Chosen over a dockerless embedded Postgres (zonky) and over reusing the shared docker-compose DB, because Testcontainers is the industry standard for Flyway integration tests, is self-contained/clean-slate per run, and avoids a stateful shared database. Only **one** new test dependency is needed — the PG JDBC driver (`org.postgresql:postgresql`) and `flyway-database-postgresql` are already on the classpath. Requires a Docker daemon **only** when the integration test runs (never for the H2 suite).

**Test Gating**
- **D-02:** The Docker-requiring integration test is **opt-in**, not part of the default `mvn test`. Gate it via a JUnit `@Tag` (and/or a `*IT` naming convention with Surefire/Failsafe) plus a Maven profile so it runs via something like `mvn verify -Pintegration`. The default `mvn test` stays **H2-only and Docker-free** (the existing 120-test suite must not start requiring Docker). The profile/command MUST be clearly documented (README/CLAUDE.md) so it can be run deliberately and wired into CI. This satisfies the roadmap's "runs in `mvn test` (or a clearly-documented `mvn` profile)" allowance.

**V9 Merge Staging**
- **D-03:** Build the duplicate-club "before" state **in test code**: migrate Flyway to **V8** (`flyway.target=8` / stop before the merge), insert duplicate-club fixtures via **JDBC/SQL** (clubs sharing `(sport_id, external_id)`, each with players, `team_league`-precursor `league_id` links, and favourites rows), then run **V9** and assert the merge. The fixture lives in the test — visible, precise, and it does **not** pollute the production migration location. Preferred over adding a test-only Flyway seed migration ordered before V9 (which is fragile on version ordering and risks leaking into the prod migration path).

**Assertion Depth**
- **D-04:** Assert V9 **exhaustively** — every documented guarantee:
  - duplicate clubs sharing `(sport_id, external_id)` merged into a single canonical row (dupes deleted);
  - `team_league` join table populated (each club→competition link present, backfilled from the old `team.league_id`);
  - **players, league-links, AND favourites** all re-pointed onto the canonical club/player rows, **respecting the `FavoriteTeam(user_id, team_id)` / `FavoritePlayer(user_id, player_id)` unique constraints** (the merge de-dupes favourites);
  - `team.league_id` column dropped;
  - `Team.sport` (`sport_id`) NOT NULL + FK backfilled from the league's sport.
  Plus V8: both `name_normalized` columns exist, are indexed, and are backfilled accent-stripped/lower-cased. The favourites re-pointing + unique-constraint handling is the subtlest, highest-bug-risk part of the merge, so it is explicitly in scope.

### Claude's Discretion
- Exact test class/package name and file layout, the specific new test profile file name (e.g. `application-it.yml` mirroring `application-test.yml`), the precise Testcontainers wiring style (`@Container` static field vs `@ServiceConnection` vs manual `spring.datasource.*` override), the Maven plugin choice for gating (Failsafe vs a profiled Surefire include/exclude by tag), and the JDBC-vs-`JdbcTemplate` mechanism for seeding/asserting are left to research + planning. Any new Java carries junior-developer inline comments (project hard rule).

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope.
</user_constraints>

## Phase Requirements

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| HARD-02 | Add a real-Postgres integration test that verifies V8 (`name_normalized` backfill + indexes) and V9 (team↔league M:N data merge: duplicate-club/player merge, favourite/link re-pointing, `team.league_id` drop) Flyway migrations produce the expected schema and data. | This document's Standard Stack (verified Testcontainers/Flyway/Failsafe versions — all already managed by the Spring Boot 3.4.4 BOM), Architecture Patterns (staged-migration + fixture-building recipe), Code Examples, and Validation Architecture sections give a concrete, test-by-test path to HARD-02's 4 roadmap success criteria. |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

- **Junior-developer inline comments hard rule** — every new Java file (the IT class itself) must carry plain-English comments explaining intent, matching the style already used in `V8__add_name_normalized.sql`, `V9__team_league_many_to_many.sql`, `TextNormalizer.java`, and the existing test classes (`GIVEN`/`WHEN`/`THEN` structure per `.planning/codebase/TESTING.md`).
- **"Flyway is OFF in H2 tests"** — `application-test.yml` MUST NOT be touched by this phase (confirmed unchanged in CONTEXT.md's canonical refs); the new IT is fully separate infrastructure.
- **Mock all external providers, except this phase** — CONTEXT.md's `<domain>` section explicitly calls out that this is "the deliberate exception to the project's 'mock all external providers' rule" — a real Postgres container is correct here, not a mock.
- **"Never call live APIs" in tests** — reinforces the plain-JUnit5/no-Spring-context recommendation below: booting a full `@SpringBootTest` context would fire `DataLoader`/`NbaDataLoader`/`NflDataLoader` (all `ApplicationRunner` beans), which attempt live external-API calls at context startup. Avoiding a Spring context for this IT sidesteps that entirely.
- **Existing 120-test H2 baseline must not regress** — `mvn test` after this phase must still show 120 passing tests, 0 requiring Docker.

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `org.testcontainers:postgresql` | **1.20.6** (no explicit `<version>` needed — managed by `spring-boot-dependencies:3.4.4`'s `testcontainers.version` property, imported as a BOM) | Ephemeral `postgres:16-alpine` container per test run | `[VERIFIED: repo.maven.apache.org spring-boot-dependencies:3.4.4 POM]` — fetched and grepped directly: `<testcontainers.version>1.20.6</testcontainers.version>`, imported as `testcontainers-bom` |
| `org.testcontainers:junit-jupiter` | **1.20.6** (same managed BOM) | Provides `@Testcontainers` / `@Container` JUnit 5 lifecycle extension | `[VERIFIED: same POM]` |
| `org.flywaydb:flyway-core` + `flyway-database-postgresql` | **10.20.1** (already present in `pom.xml`, no change needed) | Programmatic staged migration via `Flyway.configure().target(String)` | `[VERIFIED: mvn dependency:tree]` — already resolves to 10.20.1; `[VERIFIED: local javap bytecode inspection]` of `flyway-core-10.20.1.jar`'s `FluentConfiguration` class confirms `target(String)`, `target(MigrationVersion)`, `baselineVersion(String)`, `baselineOnMigrate(boolean)` all exist on this exact installed version |
| `org.springframework:spring-jdbc` | **6.2.5** (already transitively present via `spring-boot-starter-data-jpa` → `spring-boot-starter-jdbc`) | `JdbcTemplate` for fixture seeding + outcome assertions | `[VERIFIED: mvn dependency:tree]` — no new dependency needed |
| `maven-failsafe-plugin` | **3.5.2** (already plugin-managed by `spring-boot-starter-parent:3.4.4`, with `integration-test` + `verify` goals pre-bound in `<executions>`) | Runs `*IT.java` classes in the `integration-test`/`verify` phases, separate from Surefire's `test` phase | `[VERIFIED: repo.maven.apache.org spring-boot-starter-parent:3.4.4 POM]` — fetched directly; plugin is in `pluginManagement` with `<goals><goal>integration-test</goal><goal>verify</goal></goals>` already configured. Only needs to be **declared** (not configured) in `<build><plugins>` to activate. |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `org.springframework.boot:spring-boot-testcontainers` | 3.4.4 (managed) | `@ServiceConnection` auto-wiring of a Testcontainers datasource into a Spring context | Only if the planner chooses the alternative `@SpringBootTest`-based approach (see Alternatives below) instead of the recommended plain-JUnit5 approach. Not needed for the primary recommendation. |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Plain JUnit 5 + raw `Flyway`/`JdbcTemplate` (recommended) | `@SpringBootTest` + `@ServiceConnection` PostgreSQLContainer + `application-it.yml` (`spring.flyway.enabled=false`, manual Flyway calls against `@Autowired DataSource`) | Gains: Spring-managed container lifecycle wiring is declarative; could reuse `TeamRepository`/`PlayerRepository` for assertions. Loses: `DataLoader`/`NbaDataLoader`/`NflDataLoader` (all `ApplicationRunner`s) fire during context startup and attempt live external calls unless explicitly excluded/profiled off; slower (full context boot ~2-5s extra); Spring Boot's single-shot auto-Flyway can't naturally pause between V8 and V9 for fixture injection, so manual Flyway calls are needed anyway — negating most of the benefit while adding startup cost and a new `application-it.yml` to maintain. |
| JDBC-only fixture seeding (recommended, D-03) | A test-only Flyway "seed" migration file ordered between V8 and V9 (e.g. `V8.1__test_seed.sql`) | CONTEXT.md D-03 explicitly rejects this: "fragile on version ordering and risks leaking into the prod migration path." Confirmed correct — `src/main/resources/db/migration` is scanned by the real app's Flyway config in prod too, so any file placed there is live production migration SQL. |
| Failsafe `*IT.java` naming convention (recommended) | JUnit 5 `@Tag("integration")` + Surefire `excludedGroups`/`includedGroups` | Both work; naming convention needs zero Surefire configuration changes (Surefire's default includes never match `*IT.java`), whereas `@Tag` requires explicitly configuring Surefire's `excludedGroups` to keep `mvn test` clean. The naming convention is simpler and is exactly what Failsafe was designed for. A `@Tag` can still be added as defense-in-depth with no downside. |

**Installation:**
```xml
<!-- Add to pom.xml <dependencies> -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```
No `<version>` tags needed on either — `spring-boot-starter-parent:3.4.4` already manages `testcontainers.version=1.20.6` and imports `testcontainers-bom`.

**Version verification:** Confirmed by fetching and grepping the actual `spring-boot-dependencies-3.4.4.pom` and `spring-boot-starter-parent-3.4.4.pom` from `repo.maven.apache.org` (not training-data recall), and by running `mvn dependency:tree` / `javap` against this project's own resolved jars. All versions above are current as of Spring Boot 3.4.4 (released 2026-03-20 per Spring's own blog) and require zero pins.

## Package Legitimacy Audit

> Ecosystem is **Maven** — `gsd-tools package-legitimacy check` only supports `npm|pypi|crates`, so this audit was performed **manually** against the authoritative Maven Central / Spring Boot BOM sources instead of the automated seam.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|--------------|---------|-------------|
| `org.testcontainers:postgresql` | Maven Central | Testcontainers project, active since ~2016; this specific 1.20.x line released 2025 | Extremely high (de facto standard for JVM DB integration testing; officially co-managed by the Spring Boot team's own BOM) | `github.com/testcontainers/testcontainers-java` | OK | Approved |
| `org.testcontainers:junit-jupiter` | Maven Central | Same project/org as above | Same tier | `github.com/testcontainers/testcontainers-java` | OK | Approved |
| `org.springframework.boot:spring-boot-testcontainers` | Maven Central | Official Spring Boot module, introduced Spring Boot 3.1 (2023), version pinned to the Spring Boot release itself | Official first-party Spring artifact | `github.com/spring-projects/spring-boot` | OK | Approved (optional — see Alternatives) |
| `maven-failsafe-plugin` | Maven Central | Official Apache Maven plugin, decades old | Extremely high, canonical IT-phase tool | `github.com/apache/maven-surefire` | OK | Approved (already plugin-managed by the project's own parent POM — zero new supply-chain surface) |

**Packages removed due to [SLOP] verdict:** none.
**Packages flagged as suspicious [SUS]:** none. All four packages are first-party artifacts from the Spring Boot team or the Testcontainers organization, both already transitively referenced by this project's own build (Spring Boot's own dependency-management BOM lists exact managed versions for all of them), which is about as strong a legitimacy signal as exists outside npm/PyPI registry heuristics.

## Architecture Patterns

### System Architecture Diagram

```
                    ┌─────────────────────────────────────────────┐
                    │  mvn verify -Pintegration                    │
                    │  (opt-in; requires local Docker daemon)       │
                    └───────────────────┬───────────────────────────┘
                                        │ activates
                                        ▼
                    ┌─────────────────────────────────────────────┐
                    │ Maven Failsafe Plugin (integration-test phase)│
                    │  scans src/test/java for **/*IT.java          │
                    └───────────────────┬───────────────────────────┘
                                        │ runs
                                        ▼
┌───────────────────────────────────────────────────────────────────────┐
│ PostgresMigrationIT  (plain JUnit 5, NO @SpringBootTest)                │
│                                                                          │
│  @Testcontainers(disabledWithoutDocker = true)                          │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │ @Container static PostgreSQLContainer<?> postgres                │   │
│  │   = new PostgreSQLContainer<>("postgres:16-alpine")               │   │
│  └─────────────────────────────────┬───────────────────────────────┘   │
│                                    │ getJdbcUrl()/getUsername()/pw()    │
│                                    ▼                                    │
│  @BeforeAll:                                                            │
│   1. Flyway.configure().dataSource(url,u,p).target("8").load().migrate()│
│      ── runs V1→V8 against the fresh container ──                      │
│   2. JdbcTemplate seeds duplicate-club fixtures (raw SQL INSERTs)       │
│      ── two `team` rows sharing external_id, players, favourites ──    │
│   3. Flyway.configure().dataSource(url,u,p).target("9").load().migrate()│
│      ── same schema_history table → applies only V9 ──                 │
│                                                                          │
│  @Test methods (read-only, query the migrated+seeded DB via JdbcTemplate)│
│   • v8_nameNormalized_columnsExist_indexed_backfillable()               │
│   • v9_duplicateClubs_mergedIntoCanonicalRow()                          │
│   • v9_teamLeagueJoinTable_populated()                                  │
│   • v9_favouriteTeam_repointed_respectingUniqueConstraint()             │
│   • v9_favouritePlayer_repointed_respectingUniqueConstraint()           │
│   • v9_teamLeagueIdColumn_dropped()                                     │
│   • v9_teamSportId_notNull_andForeignKeyed()                            │
└───────────────────────────────────────────────────────────────────────┘
```

Reading the primary use case: `mvn verify -Pintegration` → Failsafe discovers `PostgresMigrationIT` → the class boots one shared Postgres container → runs Flyway to V8 → injects fixtures via raw JDBC → runs Flyway to V9 → each `@Test` method independently queries the now-fully-migrated database and asserts one documented guarantee. No production code (`src/main`) is touched; no Spring context is booted; no external API is called.

### Recommended Project Structure

```
src/test/java/com/onestopsports/
├── migration/                          # NEW package — mirrors the "one concern, one package" convention
│   └── PostgresMigrationIT.java        # The one new file this phase needs (Failsafe picks it up by name)
├── controller/  service/  ...          # existing — untouched
└── ...

src/test/resources/
├── application-test.yml                # existing H2 profile — UNTOUCHED per D-02/canonical_refs
└── (no new file needed — the IT never loads a Spring context or application-*.yml)

pom.xml
├── <dependencies>                      # + org.testcontainers:postgresql, org.testcontainers:junit-jupiter (test scope)
└── <profiles>
    └── <profile id="integration">      # NEW — wraps <build><plugins><plugin>maven-failsafe-plugin</plugin></plugins></build>
```

### Pattern 1: Staged Flyway Migration (D-03)

**What:** Run Flyway to an intermediate target version, mutate the database out-of-band, then run Flyway again to a later target — the schema-history table on the shared connection carries state between the two `.migrate()` calls.

**When to use:** Whenever a test needs to inject state that must exist *before* a specific migration runs (here: duplicate clubs that only V9's merge logic should clean up).

**Example:**
```java
// Source: local javap verification of flyway-core-10.20.1.jar FluentConfiguration
// (confirms target(String) exists on this exact installed Flyway version)

String jdbcUrl = postgres.getJdbcUrl();
String user    = postgres.getUsername();
String pass    = postgres.getPassword();

// Stage 1: run only V1..V8 — schema_history table now records up to version "8".
Flyway.configure()
        .dataSource(jdbcUrl, user, pass)
        .locations("classpath:db/migration")   // same location the real app uses — avoids checksum drift
        .target("8")
        .load()
        .migrate();

// Stage 2 (in between): seed "before" fixtures via raw JDBC/JdbcTemplate — see Pattern 2.

// Stage 3: run only V9 — because it's the SAME jdbcUrl, Flyway sees "8" already applied
// in flyway_schema_history and applies exactly V9, nothing else.
Flyway.configure()
        .dataSource(jdbcUrl, user, pass)
        .locations("classpath:db/migration")
        .target("9")               // or MigrationVersion.LATEST — both resolve to V9 here
        .load()
        .migrate();
```

### Pattern 2: JDBC Fixture Seeding for the V9 Merge (D-03, D-04)

**What:** Insert "before" rows directly with SQL (bypassing Hibernate/JPA entirely) so the fixture is visible, precise, and independent of `DataLoader`.

**When to use:** Building the specific duplicate-club scenario V9's merge logic is designed to fix.

**Concrete fixture recipe** (exercises every branch of V9's merge SQL, addressing D-04's "subtlest, highest-bug-risk part"):

1. One `sport` row (e.g. slug `football`).
2. **Two** `league` rows under that same sport — e.g. a domestic league and a continental cup (mirrors the real-world Real Madrid / La Liga + Champions League scenario the migration's own comment describes). Both leagues share `sport_id`.
3. **Two** `team` rows that are the "duplicate club": both share the same `external_id` (e.g. `'86'`), but each is linked to a *different* league via `league_id` (this is the pre-V9 schema — `team.league_id` still exists at V8). Insert the lower-`id` row first so it becomes canonical (V9's merge picks `MIN(id)` per `(sport_id, external_id)` group — note: at V8 stage `team.sport_id` does not exist yet; V9 itself backfills `sport_id` from each row's `league_id → league.sport_id` *before* running the merge step, so the two duplicate rows correctly resolve to the same `sport_id` as long as their two leagues share `sport_id`).
4. Duplicate `player` rows: insert the **same player name** under both duplicate team rows (simulates the same squad seeded once per competition — this is exactly what V9 step 3b's `GROUP BY team_id, name` de-duplication targets).
5. `user_account` rows + `favorite_team` rows covering **both branches** of the re-point logic:
   - **Re-point branch:** User A favourites *only* the duplicate (non-canonical) team → after V9, that favourite row's `team_id` must now point at the canonical team.
   - **Collision/de-dupe branch:** User B favourites *both* the canonical team AND the duplicate team → after V9, only the canonical favourite survives; the duplicate favourite row is deleted (not re-pointed, because `NOT EXISTS` in the migration's `UPDATE` skips it, and the trailing `DELETE` removes anything left pointing at the deleted duplicate).
6. `favorite_player` rows covering the same two branches, but keyed on the duplicated **player** rows from step 4 (exercises V9 step 3b's `favorite_player` re-point + de-dupe, the mirror of step 5 at the player level).

```java
// Source: derived directly from reading V9__team_league_many_to_many.sql's merge logic
// (team_merge_map / player_merge_map temp tables, the NOT EXISTS re-point pattern)

jdbcTemplate.update(
    "INSERT INTO sport (name, slug) VALUES (?, ?)", "Futbol", "football");
Long sportId = jdbcTemplate.queryForObject(
    "SELECT id FROM sport WHERE slug = ?", Long.class, "football");

jdbcTemplate.update(
    "INSERT INTO league (sport_id, name) VALUES (?, ?)", sportId, "Domestic League");
Long domesticLeagueId = jdbcTemplate.queryForObject(
    "SELECT id FROM league WHERE name = ?", Long.class, "Domestic League");

jdbcTemplate.update(
    "INSERT INTO league (sport_id, name) VALUES (?, ?)", sportId, "Continental Cup");
Long cupLeagueId = jdbcTemplate.queryForObject(
    "SELECT id FROM league WHERE name = ?", Long.class, "Continental Cup");

// Duplicate club: same external_id, two different (pre-V9) league_id links.
jdbcTemplate.update(
    "INSERT INTO team (league_id, name, external_id) VALUES (?, ?, ?)",
    domesticLeagueId, "Real Madrid", "86");
Long canonicalTeamId = jdbcTemplate.queryForObject(
    "SELECT id FROM team WHERE external_id = ? ORDER BY id LIMIT 1", Long.class, "86");

jdbcTemplate.update(
    "INSERT INTO team (league_id, name, external_id) VALUES (?, ?, ?)",
    cupLeagueId, "Real Madrid", "86");
Long duplicateTeamId = jdbcTemplate.queryForObject(
    "SELECT id FROM team WHERE external_id = ? ORDER BY id DESC LIMIT 1", Long.class, "86");

// ... players, user_account, favorite_team, favorite_player fixtures follow the same shape.
```

### Pattern 3: Failsafe Opt-In Gating via Naming Convention + Profile (D-02)

**What:** Rely on Failsafe's own default `**/*IT.java` inclusion (never picked up by Surefire's default `mvn test`) and wrap the plugin declaration itself in a Maven profile so even `mvn verify` without the profile stays Docker-free.

```xml
<!-- Source: verified against spring-boot-starter-parent:3.4.4 POM — the plugin's
     <executions> (integration-test + verify goals) are ALREADY defined in
     pluginManagement; declaring it bare here is sufficient to activate them. -->
<profiles>
    <profile>
        <id>integration</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-failsafe-plugin</artifactId>
                    <!-- No <version> needed — managed by spring-boot-starter-parent.
                         No <configuration> needed — default include pattern is **/*IT.java,
                         which already matches PostgresMigrationIT.java. -->
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

Invocation: `mvn verify -Pintegration` (full suite) or `mvn verify -Pintegration -Dit.test=PostgresMigrationIT` (single class — note the `-Dit.test=` property, **not** Surefire's `-Dtest=`).

### Anti-Patterns to Avoid

- **Booting a full `@SpringBootTest` context for this IT:** fires `DataLoader`/`NbaDataLoader`/`NflDataLoader` `ApplicationRunner` beans, which attempt live external-API calls unless explicitly excluded — violates the project's "never call live APIs in tests" rule and D-01's hermeticity requirement, and can't naturally pause mid-Flyway-chain anyway.
- **Placing test fixtures in `src/main/resources/db/migration`:** explicitly rejected by D-03 — that directory is scanned by the real production Flyway config too.
- **Configuring Surefire's `excludes` to keep the IT out of `mvn test`:** unnecessary — Surefire's default include patterns simply never match `*IT.java`. Adding an exclude is redundant defensive config, not wrong, but not required.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|--------------|-----|
| Docker-availability detection so the IT skips cleanly when no daemon is running | A custom `DockerClientFactory.instance().isDockerAvailable()` check wired into a JUnit 5 `ExecutionCondition` | `@Testcontainers(disabledWithoutDocker = true)` on the test class | `[CITED: java.testcontainers.org/quickstart/junit_5_quickstart/]` — this exact flag exists precisely for this case; "Tests should be skipped instead of failing because Docker is unavailable." |
| Waiting for Postgres to accept connections before running Flyway | A manual retry/sleep loop polling `SELECT 1` | `PostgreSQLContainer.start()` (implicitly invoked by the `@Testcontainers` extension before `@BeforeAll`) — it blocks until the container's built-in JDBC wait strategy succeeds | Testcontainers' `PostgreSQLContainer` ships a JDBC-connectivity wait strategy by default; hand-rolling this reintroduces flakiness Testcontainers already solved |
| Building the JDBC URL/credentials by hand | String-concatenating a `jdbc:postgresql://localhost:<port>/...` URL from a manually-mapped container port | `postgres.getJdbcUrl()`, `.getUsername()`, `.getPassword()` | The container's exposed port is dynamically assigned; hand-rolling the URL is exactly the kind of brittleness Testcontainers exists to remove |
| "Run migrations to X, then to Y" | Manually executing raw `.sql` file contents via JDBC in file-order up to a cutoff | `FluentConfiguration.target(String)` (verified present in the project's own Flyway 10.20.1) | Flyway already tracks per-migration checksums/state in `flyway_schema_history`; hand-rolling a cutoff mechanism duplicates and risks diverging from that |

**Key insight:** everything this phase needs — container orchestration, staged migration, opt-in test-phase gating — already has a first-party, already-transitively-managed tool in this exact dependency tree. The only genuinely new code is the fixture-building SQL and the assertion SQL, both inherently project-specific and not hand-roll-able away.

## Common Pitfalls

### Pitfall 1: Raw JDBC fixtures bypass the `@PrePersist` hook
**What goes wrong:** Seeding via `JdbcTemplate` INSERTs (as D-03 requires) never triggers `Team`/`Player`'s `@PrePersist`/`@PreUpdate` `syncNameNormalized()` hook, nor does it invoke `NameNormalizationBackfill` (a Spring `ApplicationRunner` requiring a JPA context). So `name_normalized` will stay however the test sets it — it will NOT self-populate the way it does in the real running app.
**Why it happens:** The whole point of the plain-JUnit5 approach is to avoid booting a Spring/JPA context; but that context is exactly what owns the auto-normalization behavior.
**How to avoid:** For V8 assertions, directly reuse the real production algorithm — `com.onestopsports.util.TextNormalizer.normalize(String)` is a trivial, dependency-free public static method already on the test classpath (same module). Compute the expected normalized value with it and either (a) explicitly set `name_normalized` on INSERT to prove the column/index round-trips correctly, or (b) insert with `name_normalized = NULL` and assert the column accepts NULL (matching V8's "nullable on purpose" comment) while separately asserting `TextNormalizer.normalize(rawName)` produces the value the column is *designed* to hold. Document explicitly in the test which of these two interpretations of "backfilled" is being asserted — this phase's IT verifies the migration's *schema shape*, not `NameNormalizationBackfill`'s own runtime logic (that class would need its own unit test with mocked repositories if deeper coverage is wanted — likely out of this phase's scope).
**Warning signs:** A V8 assertion that expects `name_normalized` to be non-null immediately after a raw-JDBC INSERT with no explicit value set will fail — that's the hook not firing, not a migration bug.

### Pitfall 2: Surefire accidentally picking up the IT class
**What goes wrong:** If the new class is named `PostgresMigrationTest.java` instead of `...IT.java`, Surefire's default include pattern (`**/*Test.java`) WILL pick it up, and `mvn test` starts requiring Docker — breaking D-02 and the existing 120-test green baseline for anyone without Docker running.
**Why it happens:** Easy naming slip, especially since every other test class in the codebase is named `*Test.java`.
**How to avoid:** Name the class `PostgresMigrationIT.java` (Failsafe's default convention: `IT*.java`, `*IT.java`, `*ITCase.java`). After adding it, run `mvn test` locally with Docker **stopped** and confirm the suite still passes and the new class is never invoked.
**Warning signs:** `mvn test` (no profile) suddenly fails or hangs when Docker Desktop isn't running.

### Pitfall 3: H2 `MODE=PostgreSQL` false confidence (pre-existing, reaffirmed)
**What goes wrong:** Believing the existing H2-based suite already covers V8/V9 because `application-test.yml` runs H2 in `MODE=PostgreSQL` compatibility mode.
**Why it happens:** H2's PostgreSQL compatibility mode only approximates SQL *syntax*; it cannot execute Postgres-specific migration internals like `CREATE TEMP TABLE ... AS SELECT`, `ON CONFLICT DO NOTHING`, or the specific `information_schema`/`pg_indexes` catalog views this IT's assertions query. This is precisely why `application-test.yml` sets `flyway.enabled: false` and lets Hibernate's `create-drop` build the schema directly from the entity mapping instead — V8/V9 have literally never executed against H2.
**How to avoid:** This phase's entire premise is fixing this gap — don't accidentally treat the new IT as "extra/optional" coverage; it's the *only* place these two migrations are ever executed.
**Warning signs:** N/A — this is documentation/framing, not a code risk, but worth stating explicitly in the IT's own class-level comment per the project's teaching-comment convention.

### Pitfall 4: Flyway checksum/location mismatch
**What goes wrong:** If the IT's `Flyway.configure()` points at a different `locations(...)` value than `classpath:db/migration` (e.g. a copied/duplicated migration folder under `src/test/resources`), Flyway will either fail to find the real migrations or (worse) silently validate against a stale copy that drifts from `src/main/resources/db/migration` over time.
**Why it happens:** Some Testcontainers+Flyway tutorials show a separate test migration location as a pattern; this project does not need that since V8/V9 are the *actual* production migrations under test, not test-only schema setup.
**How to avoid:** Explicitly set `.locations("classpath:db/migration")` (the same value `application.yml`/`application-prod.yml` use) so the IT runs the exact files that ship to production, with zero duplication.
**Warning signs:** `FlywayValidateException` on `.migrate()`, or successful-but-wrong-schema runs.

### Pitfall 5: Temp tables from V9's own SQL aren't queryable after `.migrate()` returns
**What goes wrong:** V9's SQL creates `CREATE TEMP TABLE team_merge_map ...` / `CREATE TEMP TABLE player_merge_map ...` as part of the migration script itself. These are session-scoped temp tables; they exist only within the connection/transaction Flyway used to execute that migration and are gone once that migration's connection closes (Flyway typically uses per-migration transactions/connections).
**Why it happens:** It's tempting to want to inspect the merge mapping directly to "prove" the algorithm worked.
**How to avoid:** Assert final-state outcomes instead — the real, permanent tables (`team`, `player`, `team_league`, `favorite_team`, `favorite_player`) after V9 completes. This is exactly what D-04's exhaustive-assertion list already specifies (row counts, re-pointing, dropped column), so no test design change is needed — just don't try to query the temp tables.
**Warning signs:** `relation "team_merge_map" does not exist` if a test naively tries to query it post-migration.

### Pitfall 6: Guessing Postgres's auto-generated unique-constraint names
**What goes wrong:** `favorite_team` and `favorite_player`'s `UNIQUE (user_id, team_id)` / `UNIQUE (user_id, player_id)` constraints (from V3) are declared **inline without an explicit `CONSTRAINT` name** in both the SQL (`V3__create_user_favorites.sql`) and the JPA annotation (`@UniqueConstraint(columnNames = {...})` with no `name=` attribute on `FavoriteTeam`/`FavoritePlayer`). Postgres auto-generates a name (conventionally `<table>_<col1>_<col2>_key`), but hardcoding that guessed string in an assertion is brittle `[ASSUMED]` — Postgres's exact auto-naming algorithm is a documented convention, not independently verified against this project's exact Postgres 16 instance in this research session.
**How to avoid:** Prefer a **behavioral** assertion — attempt to INSERT a row that would duplicate an existing `(user_id, team_id)`/`(user_id, player_id)` pair after the merge and assert it throws (SQLState `23505`, unique violation) — this proves the constraint is enforced regardless of its generated name. If an existence check by name is still wanted, query `information_schema.table_constraints` filtered by `table_name` + `constraint_type = 'UNIQUE'` (returns the actual name Postgres assigned) rather than hardcoding a guessed string.
**Exception:** `team.sport_id`'s foreign key IS explicitly named in V9's SQL (`ALTER TABLE team ADD CONSTRAINT fk_team_sport FOREIGN KEY ...`) — safe to assert by the exact literal name `fk_team_sport`.

### Pitfall 7: Container startup latency and first-run image pull
**What goes wrong:** The very first local/CI run of this profile needs network access to pull `postgres:16-alpine` (a few hundred MB); subsequent runs reuse the local Docker image cache and start in ~1-3 seconds.
**How to avoid:** Document in README/CLAUDE.md (per D-02) that `mvn verify -Pintegration` requires both a running Docker daemon AND (on first run) network access to pull the image. Not a functional risk, just a documentation/CI-setup note — CI pipelines should pre-pull or cache the image layer.

## Code Examples

### Class skeleton (ties Patterns 1–3 together)

```java
// Source: synthesized from this project's own migration SQL (V1–V9) + verified
// Flyway 10.20.1 / Testcontainers 1.20.6 APIs (see Standard Stack for citations).
package com.onestopsports.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

// Verifies the two Flyway migrations (V8, V9) that only ever run against real
// Postgres — Flyway is disabled in the H2 unit-test profile, so this is the
// ONLY place their schema/data changes are ever exercised. Deliberately does
// NOT boot a Spring context: booting one would trigger DataLoader/NbaDataLoader/
// NflDataLoader (ApplicationRunner beans that call live external APIs), which
// this project's tests must never do, and would prevent the two-stage
// (target=8, seed, target=9) migration this test needs.
@Testcontainers(disabledWithoutDocker = true) // Skips cleanly (not a failure) when no Docker daemon is running.
class PostgresMigrationIT {

    // A fresh postgres:16-alpine container per test class run — matches the
    // version pinned in docker-compose.yml so behaviour matches local/prod Postgres.
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static JdbcTemplate jdbc;

    @BeforeAll
    static void migrateAndSeed() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);

        // Stage 1: V1..V8 only — the schema BEFORE V9's merge logic runs.
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target("8")
                .load()
                .migrate();

        seedDuplicateClubFixtures(jdbc); // Pattern 2 — raw SQL, see Architecture Patterns section

        // Stage 2: run V9 on top — same connection details, so Flyway's
        // schema_history table (already recording up to "8") applies only V9.
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target("9")
                .load()
                .migrate();
    }

    @Test
    void v8_nameNormalizedColumns_existAndAreIndexed() {
        // GIVEN the V1..V9 chain has run (V8 added these columns)
        // WHEN we ask Postgres's own catalog whether the columns exist
        Integer teamColCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'team' AND column_name = 'name_normalized'",
                Integer.class);
        Integer playerColCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'player' AND column_name = 'name_normalized'",
                Integer.class);
        // THEN both columns exist
        assertThat(teamColCount).isEqualTo(1);
        assertThat(playerColCount).isEqualTo(1);

        // AND both are indexed (V8's idx_team_name_normalized / idx_player_name_normalized)
        Integer teamIdxCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'team' AND indexname = 'idx_team_name_normalized'",
                Integer.class);
        assertThat(teamIdxCount).isEqualTo(1);
    }

    @Test
    void v9_teamLeagueIdColumn_isDropped() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'team' AND column_name = 'league_id'",
                Integer.class);
        assertThat(count).isZero(); // V9's final ALTER TABLE team DROP COLUMN league_id
    }

    @Test
    void v9_teamSportId_isNotNull_andForeignKeyed() {
        String nullable = jdbc.queryForObject(
                "SELECT is_nullable FROM information_schema.columns WHERE table_name = 'team' AND column_name = 'sport_id'",
                String.class);
        assertThat(nullable).isEqualTo("NO");

        Integer fkCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_name = 'fk_team_sport'",
                Integer.class);
        assertThat(fkCount).isEqualTo(1); // Explicitly named in V9's SQL — safe to assert by literal name.
    }

    // ... additional @Test methods for the club merge / team_league population /
    // favourite re-pointing + collision-skip / player de-dupe branches — see
    // Validation Architecture for the full test-by-test map.

    private static void seedDuplicateClubFixtures(JdbcTemplate jdbc) {
        // See Architecture Patterns → Pattern 2 for the full recipe.
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|-------------------|---------------|--------|
| Manually wiring `spring.datasource.url` via `@DynamicPropertySource` to a Testcontainers container | `@ServiceConnection` on a `@Bean`/`@Container` field (Spring Boot 3.1+) | Spring Boot 3.1 (2023) | Not used in this phase's recommended approach (no Spring context at all), but relevant if the planner chooses the `@SpringBootTest` alternative — `@DynamicPropertySource` still works and is not deprecated, `@ServiceConnection` is simply less boilerplate. |
| `zonky.test.db` embedded Postgres for Flyway tests | Testcontainers real Docker Postgres | Long-standing industry shift; explicitly the reasoning behind D-01 | Testcontainers gives byte-for-byte the same Postgres version as prod/docker-compose; embedded-Postgres solutions can diverge on edge-case SQL behavior (exactly the risk this phase exists to eliminate). |

**Deprecated/outdated:** none directly relevant — this is a greenfield addition to the test suite, not a migration off an existing pattern.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|----------------|
| A1 | Postgres auto-generates unique-constraint names in the form `<table>_<col1>_<col2>_key` for inline `UNIQUE(...)` clauses with no explicit `CONSTRAINT` name | Common Pitfalls #6 | Low — research explicitly recommends NOT relying on this guessed name; behavioral (attempt-duplicate-insert) assertion is the primary recommendation, so this assumption doesn't gate any required test design decision. |
| A2 | A full `@SpringBootTest`-based `PostgresMigrationIT` would trigger `DataLoader`/`NbaDataLoader`/`NflDataLoader` `ApplicationRunner`s during context startup (based on `CLAUDE.md`'s own note that `OneStopSportsApplicationTests`'s `@SpringBootTest` "Runs the loaders against H2") | Standard Stack → Alternatives Considered; Architecture Patterns anti-patterns | Low-medium — this claim is the main argument against the `@SpringBootTest` alternative. If the loaders in fact soft-fail instantly on missing/placeholder API keys in a test profile (plausible, given the project's "soft-fail" service pattern), the risk of a `@SpringBootTest` approach is lower than stated. Either way, the recommended plain-JUnit5 approach sidesteps this question entirely and is unaffected. |

## Open Questions

1. **Should `NameNormalizationBackfill`'s actual runtime logic be exercised, or only the V8 schema shape it depends on?**
   - What we know: `NameNormalizationBackfill` is a Spring `ApplicationRunner` requiring `TeamRepository`/`PlayerRepository` (a JPA/Spring context) — genuinely out of reach for a plain-JUnit5, no-Spring-context IT.
   - What's unclear: Whether the roadmap's "backfilled accent-stripped/lower-cased for seeded rows" success criterion (#2) is satisfied by asserting the column/index exist and correctly round-trip `TextNormalizer.normalize()`-computed values (this research's recommendation), versus requiring an actual invocation of the `NameNormalizationBackfill` bean against real rows.
   - Recommendation: Treat this phase's IT as verifying the **migration's schema shape** (columns exist, indexed, nullable, correctly typed to hold normalized values) — that is squarely "the V8 migration." If deeper coverage of `NameNormalizationBackfill`'s own backfill logic is wanted, that is better suited to a focused `@DataJpaTest`/mocked-repository unit test of that one class, arguably out of this phase's stated boundary ("changing... the H2 unit-test profile/suite, or re-testing already-covered application behavior" is explicitly out of scope). Planner should make this call explicit in the plan.

2. **Should the Maven profile also add a JUnit 5 `@Tag` for defense-in-depth?**
   - What we know: The `*IT.java` naming convention alone is sufficient to keep `mvn test` Docker-free (verified: Surefire's default includes never match it).
   - What's unclear: Whether the team wants belt-and-suspenders protection against a future accidental Surefire configuration change that widens its include pattern.
   - Recommendation: Optional. Adding `@Tag("integration")` costs nothing and matches D-02's "and/or" wording exactly; the planner can include it as a low-cost extra safety net without it changing any other design decision.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|--------------|-----------|---------|----------|
| Docker daemon | Testcontainers `PostgreSQLContainer` (the entire IT) | ✗ (CLI present, daemon not currently running in this research session) | Docker CLI 29.3.1 present; daemon socket not reachable (`docker ps` → "no such file or directory") | `@Testcontainers(disabledWithoutDocker = true)` skips the test class cleanly (reported as skipped, not failed) rather than erroring the build — the developer/CI simply needs to start Docker Desktop / the daemon before running `mvn verify -Pintegration` |
| Network access (first run only) | Pulling the `postgres:16-alpine` image | Not verified in this session | — | Pre-pull the image (`docker pull postgres:16-alpine`) or rely on CI's Docker layer cache; document this in the "how to run" note per D-02 |

**Missing dependencies with no fallback:** none — Docker itself has a clean, documented fallback (skip, don't fail) via `disabledWithoutDocker = true`, which is exactly what an *opt-in* integration profile should do.

**Missing dependencies with fallback:**
- Docker daemon not running → IT is skipped, not failed, via `@Testcontainers(disabledWithoutDocker = true)`. The default `mvn test`/`mvn verify` (no `-Pintegration`) never even attempts to load the plugin, so this is a non-issue for the default build path.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) + Testcontainers (`org.testcontainers:junit-jupiter`, `org.testcontainers:postgresql`) + Maven Failsafe Plugin 3.5.2 |
| Config file | New: a Maven `<profile id="integration">` block in `pom.xml` wrapping the (already plugin-managed) `maven-failsafe-plugin` declaration. No new `application-*.yml` — the IT boots no Spring context. |
| Quick run command | `mvn verify -Pintegration -Dit.test=PostgresMigrationIT` |
| Full suite command | `mvn verify -Pintegration` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|---------------------|--------------|
| HARD-02 | Full V1→V9 chain runs against real Postgres (roadmap criterion 1) | integration | `mvn verify -Pintegration -Dit.test=PostgresMigrationIT` (the `@BeforeAll` migration itself is the assertion — a failed `.migrate()` throws `FlywayException`, failing the whole class) | ❌ Wave 0 |
| HARD-02 | `team.name_normalized` + `player.name_normalized` exist, indexed (roadmap criterion 2) | integration | `mvn verify -Pintegration -Dit.test=PostgresMigrationIT#v8_nameNormalizedColumns_existAndAreIndexed` | ❌ Wave 0 |
| HARD-02 | `name_normalized` correctly holds accent-stripped/lower-cased values for seeded rows (roadmap criterion 2) | integration | `...#v8_nameNormalized_roundTripsTextNormalizerOutput` | ❌ Wave 0 |
| HARD-02 | Duplicate clubs sharing `(sport_id, external_id)` merged into one canonical row, dupes deleted (roadmap criterion 3) | integration | `...#v9_duplicateClubs_mergedIntoCanonicalRow` | ❌ Wave 0 |
| HARD-02 | `team_league` join table populated from old `league_id` links, both original + re-pointed | integration | `...#v9_teamLeagueJoinTable_populated` | ❌ Wave 0 |
| HARD-02 | Players re-pointed onto canonical club + de-duplicated by `(team_id, name)` | integration | `...#v9_duplicatePlayers_repointedAndDeduplicated` | ❌ Wave 0 |
| HARD-02 | `favorite_team` re-pointed onto canonical club (happy path) | integration | `...#v9_favoriteTeam_repointedToCanonical_whenNoCollision` | ❌ Wave 0 |
| HARD-02 | `favorite_team` collision-skip: dup favourite deleted (not duplicated) when user already favourited canonical | integration | `...#v9_favoriteTeam_dupDeleted_whenCanonicalAlreadyFavorited` | ❌ Wave 0 |
| HARD-02 | `favorite_player` re-pointed onto canonical player (happy path) | integration | `...#v9_favoritePlayer_repointedToCanonical_whenNoCollision` | ❌ Wave 0 |
| HARD-02 | `favorite_player` collision-skip mirrors the team-level behavior | integration | `...#v9_favoritePlayer_dupDeleted_whenCanonicalAlreadyFavorited` | ❌ Wave 0 |
| HARD-02 | `team.league_id` column dropped (roadmap criterion 3) | integration | `...#v9_teamLeagueIdColumn_isDropped` | ❌ Wave 0 |
| HARD-02 | `team.sport_id` NOT NULL + FK backfilled from league's sport | integration | `...#v9_teamSportId_isNotNull_andForeignKeyed` | ❌ Wave 0 |
| HARD-02 | Existing H2 suite (120 tests) unaffected, Docker-free (roadmap criterion 4) | regression (existing) | `mvn test` (no profile) | ✅ existing — just re-run, no new file |

### Sampling Rate
- **Per task commit:** `mvn test` (fast H2 suite — no Docker needed; proves no regression while building the IT out)
- **Per wave merge:** `mvn verify -Pintegration` (full suite including the new Docker-requiring IT)
- **Phase gate:** Both `mvn test` (120 tests, green, Docker-free) AND `mvn verify -Pintegration` (green, with Docker running) before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `src/test/java/com/onestopsports/migration/PostgresMigrationIT.java` — covers HARD-02 (all rows in the table above)
- [ ] `pom.xml` — add `org.testcontainers:postgresql` + `org.testcontainers:junit-jupiter` (test scope, no version needed) and the new `integration` Maven profile wrapping `maven-failsafe-plugin`
- [ ] README.md / CLAUDE.md — document the `mvn verify -Pintegration` command and its Docker prerequisite (required by D-02: "clearly documented... so it can be run deliberately and wired into CI")
- No shared fixture/conftest file is needed beyond the IT class itself — D-03 deliberately keeps the fixture self-contained in test code, not a shared helper (only one test class consumes it in this phase).

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|----------------|---------|---------------------|
| V2 Authentication | No | This phase adds no auth surface — pure test infrastructure |
| V3 Session Management | No | N/A |
| V4 Access Control | No | N/A |
| V5 Input Validation | Marginal | The fixture-seeding/assertion SQL should use `JdbcTemplate`'s parameterized `?` placeholders throughout (never string-concatenated SQL), even though all inputs are hardcoded test literals — matches the project's own conventions elsewhere and avoids modeling an unsafe pattern anywhere in the codebase |
| V6 Cryptography | No | The Testcontainers Postgres instance uses ephemeral, auto-generated credentials scoped to `localhost` for the lifetime of the test run only — no real secrets involved, nothing to encrypt |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|-----------------------|
| SQL injection via hand-built fixture/assertion strings | Tampering | `JdbcTemplate.update(sql, args...)` / `queryForObject(sql, Class, args...)` parameterized calls exclusively — no `String.format`/concatenation building SQL from variables, even for test-only literals |
| Accidental leakage of real project secrets into the IT (e.g. copy-pasting a real DB URL/credential) | Information Disclosure | The IT must source its connection details exclusively from `postgres.getJdbcUrl()`/`getUsername()`/`getPassword()` — never from `application-local.yml`/env vars/real Neon credentials. Since the IT boots no Spring context at all, there is no code path that could accidentally pull in `application-prod.yml`'s `${SPRING_DATASOURCE_URL}` placeholders. |

## Sources

### Primary (HIGH confidence)
- `repo.maven.apache.org/.../spring-boot-dependencies/3.4.4/spring-boot-dependencies-3.4.4.pom` — fetched directly; confirms `testcontainers.version=1.20.6`, `flyway.version=10.20.1`, and the managed `spring-boot-testcontainers:3.4.4` coordinate. `[VERIFIED]`
- `repo.maven.apache.org/.../spring-boot-starter-parent/3.4.4/spring-boot-starter-parent-3.4.4.pom` — fetched directly; confirms `maven-failsafe-plugin` version `3.5.2` and its pre-bound `integration-test`+`verify` goal executions in `pluginManagement`. `[VERIFIED]`
- Local `mvn dependency:tree` — confirms `flyway-core`/`flyway-database-postgresql` resolve to `10.20.1` and `spring-jdbc` (`6.2.5`) is already transitively present via `spring-boot-starter-data-jpa`. `[VERIFIED]`
- Local `javap` bytecode inspection of `flyway-core-10.20.1.jar`'s `FluentConfiguration` class — confirms `target(String)`, `target(MigrationVersion)`, `baselineVersion(String)`, `baselineOnMigrate(boolean)` all exist on this exact installed Flyway version. `[VERIFIED]`
- This project's own migration SQL: `V1__create_sport_league.sql` through `V9__team_league_many_to_many.sql`, `Team.java`, `Player.java`, `FavoriteTeam.java`, `TextNormalizer.java`, `NameNormalizationBackfill.java`, `application-test.yml`, `docker-compose.yml`, `pom.xml`. `[VERIFIED — read directly]`

### Secondary (MEDIUM confidence)
- Spring Boot official Testcontainers documentation — `docs.spring.io/spring-boot/reference/testing/testcontainers.html` `[CITED]`
- Testcontainers Java JUnit 5 Quickstart (`disabledWithoutDocker`) — `java.testcontainers.org/quickstart/junit_5_quickstart/` `[CITED]`
- Maven Failsafe Plugin official docs — `maven.apache.org/surefire/maven-failsafe-plugin/` `[CITED]`
- Flyway target-setting behavior (schema history continuity across two `.migrate()` calls) — `documentation.red-gate.com/fd/flyway-target-setting-277579044.html` and related Flyway/Redgate docs `[CITED]`

### Tertiary (LOW confidence)
- Various community blog posts (Medium/dev.to) on Spring Boot + Testcontainers + Flyway integration patterns — used only to cross-check that the recommended pattern (staged Flyway targets, `@ServiceConnection` alternative) is a recognized community approach, not as the source of any specific version number or API claim. `[ASSUMED — pattern-level corroboration only]`
- Postgres's exact auto-generated unique-constraint naming convention (`<table>_<col1>_<col2>_key`) — well-documented general Postgres behavior, not independently verified against this project's specific Postgres 16 instance in this research session. See Assumptions Log A1. `[ASSUMED]`

## Metadata

**Confidence breakdown:**
- Standard stack (dependency versions, plugin wiring): HIGH — every version claim was verified either by fetching the actual Spring Boot 3.4.4 POM files from Maven Central or by inspecting this project's own resolved jars/bytecode directly, not recalled from training data.
- Architecture (staged-migration + fixture pattern): HIGH — derived directly from reading the actual V8/V9 SQL and the verified Flyway API, not from generic tutorials.
- Pitfalls: HIGH for the codebase-specific pitfalls (raw-JDBC-bypasses-@PrePersist, temp-table scoping, constraint-naming), MEDIUM for general Testcontainers/Docker-availability pitfalls (well-established community knowledge, cited from official docs).

**Research date:** 2026-07-13
**Valid until:** 30 days (stable, first-party-managed dependency stack; low churn risk since all versions are pinned by the Spring Boot 3.4.4 BOM the project already depends on)
