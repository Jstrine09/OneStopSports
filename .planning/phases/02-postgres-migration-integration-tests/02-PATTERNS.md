# Phase 2: Postgres Migration Integration Tests - Pattern Map

**Mapped:** 2026-07-13
**Files analyzed:** 2 (new test class + pom.xml modification)
**Analogs found:** 2 / 2

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `src/test/java/com/onestopsports/migration/PostgresMigrationIT.java` | test (integration, no Spring context) | batch (staged Flyway migration + JDBC seed/assert) | `src/test/java/com/onestopsports/service/NbaApiServiceTest.java` (comment style/structure only — no other IT/Testcontainers/JDBC test exists in this codebase) | role-match (structure/comment conventions only; no data-flow analog exists) |
| `pom.xml` (add `org.testcontainers:postgresql`, `org.testcontainers:junit-jupiter` test-scope deps + `integration` profile wrapping `maven-failsafe-plugin`) | config (build) | n/a | `pom.xml` itself (existing `<dependencies>`/`<build><plugins>` blocks) | exact (same file, additive edit) |

No other files are created or modified — CONTEXT.md/RESEARCH.md confirm `src/main` is untouched and `application-test.yml` must NOT be edited.

## Pattern Assignments

### `src/test/java/com/onestopsports/migration/PostgresMigrationIT.java` (test, batch/staged-migration)

**Analog for structure/comment convention:** `src/test/java/com/onestopsports/service/NbaApiServiceTest.java`

This project has no existing Testcontainers/Flyway/raw-JDBC integration test, so there is no direct data-flow analog. The RESEARCH.md "Code Examples" section already supplies a concrete, project-specific class skeleton (verified against installed Flyway 10.20.1 / Testcontainers 1.20.6 APIs) — use that skeleton as the primary implementation reference. The only pattern to borrow from the existing unit-test suite is house style:

**Comment density / teaching-comment convention** (from `NbaApiServiceTest.java` lines 1-35):
```java
// Unit tests for NbaApiService — no Spring context, no real HTTP calls.
//
// How the mocking works here:
//   NbaApiService uses RestClient's fluent chain: restClient.get().uri(...).retrieve().body(Class)
//   Each step in that chain returns a different interface type. Rather than mocking each type
//   separately, we use Mockito's RETURNS_DEEP_STUBS mode which automatically creates sub-mocks
//   for every call in the chain. We then stub just the final body() call with our test data.
```
A class-level block comment precedes the class declaration, explaining *why* the test is built the way it is (not just what it does) — junior-developer-legible, per the project's hard rule (CLAUDE.md "Code Style" memory + RESEARCH.md's reaffirmation). Apply the same density to `PostgresMigrationIT`: explain why no `@SpringBootTest` is used (avoids firing `DataLoader`/`NbaDataLoader`/`NflDataLoader` `ApplicationRunner`s that call live APIs), why the migration runs in two `.target()` stages (D-03 fixture injection), and that this is the *only* place V8/V9 ever execute (Flyway is off in H2 — see Pitfall 3 in RESEARCH.md).

**Per-test comment shape** — use `GIVEN`/`WHEN`/`THEN` per `.planning/codebase/TESTING.md` convention (referenced in RESEARCH.md), e.g.:
```java
@Test
void v8_nameNormalizedColumns_existAndAreIndexed() {
    // GIVEN the V1..V9 chain has run (V8 added these columns)
    // WHEN we ask Postgres's own catalog whether the columns exist
    ...
    // THEN both columns exist
    assertThat(...);
}
```

**Concrete class skeleton to implement from** (verbatim source, RESEARCH.md "Code Examples" section, verified against this project's installed Flyway/Testcontainers versions) — package `com.onestopsports.migration`, `@Testcontainers(disabledWithoutDocker = true)`, `@Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")`, `@BeforeAll` running `Flyway.configure().dataSource(url,user,pass).locations("classpath:db/migration").target("8").load().migrate()` → JDBC-seed duplicate-club fixtures → `.target("9").load().migrate()`, then `@Test` methods querying via `JdbcTemplate` against `information_schema`/`pg_indexes`. Full listing reproduced in RESEARCH.md lines 379-487 — copy that skeleton directly rather than re-deriving it.

**Migration SQL these tests assert against** — read directly, do not re-derive identifiers:
- `src/main/resources/db/migration/V8__add_name_normalized.sql` — columns `team.name_normalized` VARCHAR(255) nullable, `player.name_normalized` VARCHAR(255) nullable; indexes named exactly `idx_team_name_normalized`, `idx_player_name_normalized`.
- `src/main/resources/db/migration/V9__team_league_many_to_many.sql` — table `team_league(team_id, league_id)` PK composite, index `idx_team_league_league`; `team.sport_id` BIGINT NOT NULL, FK named exactly `fk_team_sport`, index `idx_team_sport`; merge key `(sport_id, external_id)` picks `MIN(id)` as canonical; player de-dupe key `(team_id, name)` picks `MIN(id)`; favourites re-point uses `NOT EXISTS` on `(user_id, team_id)` / `(user_id, player_id)` then deletes remaining dup-pointed rows; final step `ALTER TABLE team DROP COLUMN league_id`.

### `pom.xml` (config, additive)

**Analog:** the file's own existing `<dependencies>` (lines 27-128) and `<build><plugins>` (lines 130-170) blocks.

**Where to add the two new test-scope dependencies** — inside the existing `<dependencies>` block, after the existing "Testing" group (lines 112-127, ends with the `h2` dependency at line 123-127):
```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope> <!-- In-memory database used instead of Postgres when running tests -->
</dependency>
<!-- NEW: Testcontainers for the real-Postgres migration IT (Phase 2) -->
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
No `<version>` needed on either — matches this pom's existing convention of omitting versions for anything managed by `spring-boot-starter-parent:3.4.4` (e.g. `spring-boot-starter-test`, `h2`, `postgresql` runtime dep at lines 60-63 already omit version tags).

**Where to add the `integration` Maven profile** — as a new top-level `<profiles>` sibling to `<build>` (the pom currently has no `<profiles>` block; insert after `</build>` at line 170, before `</project>`):
```xml
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
This exactly follows the existing `<build><plugins><plugin>` shape already used for `spring-boot-maven-plugin` (lines 132-143) and `maven-compiler-plugin` (lines 144-168) — bare `<groupId>`/`<artifactId>` with no `<version>`, since both are already parent-managed.

**Existing `<build>` block for reference** (lines 130-170, unchanged, only a new `<profiles>` sibling is added — do not touch this block):
```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
            ...
        </plugin>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            ...
        </plugin>
    </plugins>
</build>
```

## Shared Patterns

### Junior-developer inline comments (project hard rule)
**Source:** `src/main/resources/db/migration/V8__add_name_normalized.sql` and `V9__team_league_many_to_many.sql` (block comments explaining *why*, not just *what*) + `src/test/java/com/onestopsports/service/NbaApiServiceTest.java` (class-level "how this test works" block).
**Apply to:** `PostgresMigrationIT.java` — every method and the class itself needs plain-English comments a junior developer could follow, per CLAUDE.md's memory note ("All new Java must have plain-English inline comments explaining to a junior developer").

### "Never call live APIs in tests"
**Source:** CLAUDE.md Testing section + RESEARCH.md's explicit anti-pattern callout.
**Apply to:** `PostgresMigrationIT.java` must NOT use `@SpringBootTest` — booting a Spring context fires `DataLoader`/`NbaDataLoader`/`NflDataLoader` `ApplicationRunner` beans, which call live external APIs unless excluded. Use plain JUnit 5 + `@Testcontainers` with no Spring context (per RESEARCH.md's primary recommendation and CONTEXT.md's explicit sanctioning of this approach).

### `application-test.yml` must stay untouched
**Source:** `src/test/resources/application-test.yml` (H2, `flyway.enabled: false`, `cache.type: none`, Redis auto-config excluded).
**Apply to:** No changes to this file. The new IT boots no Spring context and needs no `application-it.yml` — it configures Flyway and the datasource entirely programmatically against the Testcontainers-provided Postgres instance.

### Parameterized JDBC only (no string concatenation)
**Source:** RESEARCH.md Security Domain section, consistent with this project's existing use of parameterized queries elsewhere (JPA/Hibernate everywhere else in the codebase).
**Apply to:** All `JdbcTemplate.update(sql, args...)` / `queryForObject(sql, Class, args...)` calls in the fixture-seeding and assertion code — always use `?` placeholders, never `String.format`/concatenation, even for hardcoded test literals.

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `src/test/java/com/onestopsports/migration/PostgresMigrationIT.java` (data-flow pattern specifically) | test | batch/staged-migration | No existing Testcontainers, Flyway-programmatic, or raw-JDBC test exists anywhere in this codebase — this is the first of its kind. RESEARCH.md's "Code Examples" section (verified against this project's own installed dependency versions) is the authoritative pattern source in place of a codebase analog; use it directly rather than inventing a new structure. |

## Metadata

**Analog search scope:** `src/test/java/com/onestopsports/**`, `src/main/resources/db/migration/**`, `src/test/resources/**`, `pom.xml`, `docker-compose.yml`
**Files scanned:** `NbaApiServiceTest.java`, `application-test.yml`, `V8__add_name_normalized.sql`, `V9__team_league_many_to_many.sql`, `pom.xml`
**Pattern extraction date:** 2026-07-13
