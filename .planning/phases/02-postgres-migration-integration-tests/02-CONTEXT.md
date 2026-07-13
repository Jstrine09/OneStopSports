# Phase 2: Postgres Migration Integration Tests - Context

**Gathered:** 2026-07-13
**Status:** Ready for planning

<domain>
## Phase Boundary

Prove that the two Postgres-only Flyway migrations behave correctly against a **real Postgres** instance:

- **V8** (`add_name_normalized`) — `team.name_normalized` + `player.name_normalized` columns exist, are indexed, and are backfilled accent-stripped/lower-cased for seeded rows.
- **V9** (`team_league_many_to_many`) — the one-time data merge: duplicate clubs sharing `(sport_id, external_id)` collapse into one canonical row, the `team_league` join table is populated, players/league-links/favourites are re-pointed onto the canonical rows (respecting unique constraints), and `team.league_id` is dropped.

The integration test runs the full **V1→V9** Flyway chain against real Postgres and asserts these outcomes. This is the **deliberate exception** to the project's "mock all external providers" rule — the database is real by design because H2 (with `MODE=PostgreSQL`) cannot exercise the Postgres-specific migration/merge SQL, and Flyway is turned **off** in the existing H2 unit-test profile.

**In scope:** a real-Postgres integration test (harness + isolation + assertions) for V8 and V9.
**Out of scope:** changing any migration's SQL, changing the H2 unit-test profile/suite, or re-testing already-covered application behavior.
</domain>

<decisions>
## Implementation Decisions

### Postgres Harness
- **D-01:** Use **Testcontainers** (`org.testcontainers:postgresql`) to spin up an ephemeral `postgres:16` container per integration-test run — matching the `postgres:16-alpine` version already used in `docker-compose.yml`. Chosen over a dockerless embedded Postgres (zonky) and over reusing the shared docker-compose DB, because Testcontainers is the industry standard for Flyway integration tests, is self-contained/clean-slate per run, and avoids a stateful shared database. Only **one** new test dependency is needed — the PG JDBC driver (`org.postgresql:postgresql`) and `flyway-database-postgresql` are already on the classpath. Requires a Docker daemon **only** when the integration test runs (never for the H2 suite).

### Test Gating
- **D-02:** The Docker-requiring integration test is **opt-in**, not part of the default `mvn test`. Gate it via a JUnit `@Tag` (and/or a `*IT` naming convention with Surefire/Failsafe) plus a Maven profile so it runs via something like `mvn verify -Pintegration`. The default `mvn test` stays **H2-only and Docker-free** (the existing 120-test suite must not start requiring Docker). The profile/command MUST be clearly documented (README/CLAUDE.md) so it can be run deliberately and wired into CI. This satisfies the roadmap's "runs in `mvn test` (or a clearly-documented `mvn` profile)" allowance.

### V9 Merge Staging
- **D-03:** Build the duplicate-club "before" state **in test code**: migrate Flyway to **V8** (`flyway.target=8` / stop before the merge), insert duplicate-club fixtures via **JDBC/SQL** (clubs sharing `(sport_id, external_id)`, each with players, `team_league`-precursor `league_id` links, and favourites rows), then run **V9** and assert the merge. The fixture lives in the test — visible, precise, and it does **not** pollute the production migration location. Preferred over adding a test-only Flyway seed migration ordered before V9 (which is fragile on version ordering and risks leaking into the prod migration path).

### Assertion Depth
- **D-04:** Assert V9 **exhaustively** — every documented guarantee:
  - duplicate clubs sharing `(sport_id, external_id)` merged into a single canonical row (dupes deleted);
  - `team_league` join table populated (each club→competition link present, backfilled from the old `team.league_id`);
  - **players, league-links, AND favourites** all re-pointed onto the canonical club/player rows, **respecting the `FavoriteTeam(user_id, team_id)` / `FavoritePlayer(user_id, player_id)` unique constraints** (the merge de-dupes favourites);
  - `team.league_id` column dropped;
  - `Team.sport` (`sport_id`) NOT NULL + FK backfilled from the league's sport.
  Plus V8: both `name_normalized` columns exist, are indexed, and are backfilled accent-stripped/lower-cased. The favourites re-pointing + unique-constraint handling is the subtlest, highest-bug-risk part of the merge, so it is explicitly in scope.

### Claude's Discretion
- Exact test class/package name and file layout, the specific new test profile file name (e.g. `application-it.yml` mirroring `application-test.yml`), the precise Testcontainers wiring style (`@Container` static field vs `@ServiceConnection` vs manual `spring.datasource.*` override), the Maven plugin choice for gating (Failsafe vs a profiled Surefire include/exclude by tag), and the JDBC-vs-`JdbcTemplate` mechanism for seeding/asserting are left to research + planning. Any new Java carries junior-developer inline comments (project hard rule).
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase scope & requirements
- `.planning/ROADMAP.md` — Phase 2 goal + the 4 numbered Success Criteria (the acceptance contract).
- `.planning/REQUIREMENTS.md` — requirement **HARD-02** (the migration-verification requirement this phase satisfies).

### The migrations under test
- `src/main/resources/db/migration/V8__add_name_normalized.sql` — `name_normalized` columns + indexes + backfill (V8 assertions target this).
- `src/main/resources/db/migration/V9__team_league_many_to_many.sql` — the `team_league` join creation, `team.sport_id` add/backfill, the duplicate-club/player merge + re-point of links/favourites, and the `team.league_id` drop (V9 assertions target this).
- `src/main/resources/db/migration/` (V1–V7) — the rest of the chain that must run first.

### Existing test infrastructure to mirror / not disturb
- `src/test/resources/application-test.yml` — the current H2 profile (Flyway **off**, `create-drop`, Redis excluded, `cache.type: none`). The new integration profile mirrors its Redis/cache exclusions but flips to Postgres + Flyway **on**. This file MUST stay unchanged so the fast suite is unaffected.
- `pom.xml` — already has `org.postgresql:postgresql`, `flyway-core`, `flyway-database-postgresql`; only Testcontainers (`org.testcontainers:postgresql` + BOM) is missing. The Surefire/Failsafe + profile wiring goes here.
- `docker-compose.yml` — reference for the Postgres image/version (`postgres:16-alpine`) and DB name (`onestopsports`).

### Project conventions
- `CLAUDE.md` — Flyway migrations table (V1–V9), the entity model (`Team.leagues` M:N via `team_league`, `Team.sport`, `FavoriteTeam`/`FavoritePlayer` unique constraints + `ON DELETE CASCADE`), and the "Flyway is OFF in H2 tests" note. Also the junior-developer inline-comment hard rule.
- `.planning/codebase/TESTING.md` — existing test map/conventions.

No external ADRs/specs beyond the above — requirements are fully captured in the decisions above.
</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `src/test/resources/application-test.yml` — copy its Redis-exclusion + `cache.type: none` block into the new integration profile so `@SpringBootTest` starts without Redis; the only real diff is Postgres datasource + `flyway.enabled: true` (+ let Testcontainers supply the JDBC URL).
- Existing `@SpringBootTest` context-load pattern (`OneStopSportsApplicationTests`) as the shape for a Spring-wired integration test if a full context is wanted; a slice/`@JdbcTest`-style or plain Flyway+DataSource test is also viable (planner's call).
- `flyway-database-postgresql` + `org.postgresql:postgresql` already on the classpath — no driver work needed.

### Established Patterns
- Migrations are the single source of schema truth in prod (H2 uses `ddl-auto: create-drop` instead) — the IT is the ONLY place the real V1→V9 chain is exercised.
- Entities encode the V9 target shape: `Team.leagues` (owning `@ManyToMany` via `team_league`), `Team.sport` (`sport_id`), `Team.getPrimaryLeague()`; favourites carry `@UniqueConstraint` + `ON DELETE CASCADE`. Assertions should check the DB rows, not just entity mapping.

### Integration Points
- New test source (e.g. under `src/test/java/.../migration/`), a new `src/test/resources/application-it.yml` (or Testcontainers `@ServiceConnection`), and `pom.xml` (Testcontainers dep + BOM, plus the Failsafe/profile/tag gating). Nothing in `src/main` changes.
</code_context>

<specifics>
## Specific Ideas

- V9 assertions must explicitly cover the **favourites de-dupe + unique-constraint** path and the **`team.league_id` drop** — these are called out as the subtle, highest-risk parts of the merge.
- Clean-slate per run: Testcontainers gives a fresh DB each time, so the test controls the entire pre-migration state (no reliance on seed data from `DataLoader`).
- V9 staging concretely: `flyway.target=8` → JDBC-seed duplicate clubs/players/links/favourites → run V9 → assert.
</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.
</deferred>

---

*Phase: 2-Postgres Migration Integration Tests*
*Context gathered: 2026-07-13*
