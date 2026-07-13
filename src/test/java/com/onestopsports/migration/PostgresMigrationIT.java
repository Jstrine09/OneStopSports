package com.onestopsports.migration;

import com.onestopsports.util.TextNormalizer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

// This class exists to test TWO Flyway migrations (V8 and V9) that, until this phase,
// were never actually run by any test. Why? Because our normal test suite runs against
// H2 (an in-memory database), and application-test.yml turns Flyway OFF for H2 — Hibernate
// just builds the schema straight from the @Entity classes instead. V8 and V9 use
// Postgres-only SQL features (CREATE TEMP TABLE, information_schema catalog queries the
// H2 "Postgres compatibility mode" doesn't fully support), so the only way to prove they
// work is to run them against a REAL Postgres. That's what this class does.
//
// Why no @SpringBootTest / Spring context here?
//   Booting the full Spring app would also start DataLoader, NbaDataLoader, and
//   NflDataLoader — these are ApplicationRunner beans that make LIVE calls to external
//   sports APIs (football-data.org, ESPN, etc.) the moment the app context starts up.
//   Our tests must never call live APIs (rate limits are real and shared). Skipping the
//   Spring context entirely sidesteps that problem completely, and is also faster.
//   Instead we talk to Postgres directly with Flyway's own Java API and plain JDBC
//   (via Spring's lightweight JdbcTemplate helper, which doesn't need a full context).
//
// Why does the migration run in TWO stages (target "8", then target "9")?
//   V9's whole job is to MERGE duplicate football clubs that existed under the OLD
//   one-team-per-competition schema (i.e., the schema as it looked right after V8, before
//   V9 changes it). To prove V9's merge logic actually works, we need to plant some
//   "before" data that looks like the V8-era schema, THEN run V9 on top of it. Running
//   Flyway to target "8" first, seeding fixtures by hand, and then continuing to target "9"
//   lets us do exactly that — Flyway remembers what it already ran (in the
//   flyway_schema_history table) so the second call only applies V9, not V1-V8 again.
//
// Class naming note: this file is named "...IT.java" (Integration Test), NOT "...Test.java".
// That's deliberate — Maven's default test runner (Surefire, used by `mvn test`) only picks
// up files ending in "Test"/"Tests"/"TestCase", so it will NEVER see this class, and `mvn
// test` never needs Docker. Only Maven Failsafe (activated by `mvn verify -Pintegration`)
// picks up "*IT.java" files. This is the ONLY place V8/V9 are ever exercised.
@Testcontainers(disabledWithoutDocker = true) // Skip cleanly (not "fail") if no Docker daemon is running.
class PostgresMigrationIT {

    // A brand-new, throwaway postgres:16-alpine container, spun up once for this whole
    // test class. Same Postgres version as docker-compose.yml, so behavior matches
    // local dev / production. Testcontainers automatically starts it before @BeforeAll
    // runs and stops/deletes it after all tests finish — nothing to clean up ourselves.
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    // Shared handle for both seeding fixtures and running assertions. JdbcTemplate is a
    // thin Spring helper around raw JDBC — no Spring context/beans required to use it.
    static JdbcTemplate jdbc;

    // A team we seed BEFORE V9 runs, specifically to prove V8's name_normalized column
    // correctly holds an accent-stripped/lower-cased value. Its raw (accented) name.
    private static final String SEEDED_TEAM_RAW_NAME = "Atlético Madrid";
    // Its ID (captured after INSERT) so individual @Test methods can look it up again.
    private static Long seededTeamId;
    private static Long seededLeagueId;

    @BeforeAll
    static void migrateAndSeed() {
        // Point JdbcTemplate at ONLY the Testcontainers-provided connection details.
        // We deliberately never read application-*.yml or any env var here — the whole
        // point of this test is to be self-contained and never touch real credentials.
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);

        // STAGE 1: run Flyway up through V8 only (V1..V8). After this call, the database
        // looks exactly like it did in production right before V9 was ever written — one
        // `team` row per club PER competition, connected via the old team.league_id column.
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration") // Same folder the real app ships — no duplicated/stale copy.
                .target("8")
                .load()
                .migrate();

        // In between the two migration stages: plant a "before" row via raw SQL/JDBC.
        // Raw JDBC bypasses the Team/Player entities' @PrePersist hook that normally
        // auto-computes name_normalized, so we compute it ourselves with the SAME
        // TextNormalizer class the real app uses, and set it explicitly on INSERT.
        seedFixtures(jdbc);

        // STAGE 2: run Flyway forward again, now only to V9. Because we're reusing the
        // exact same JDBC URL/credentials, Flyway's own flyway_schema_history table
        // (living inside this container) already shows "8" as applied, so this call
        // applies ONLY V9 — it does not re-run V1-V8.
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target("9")
                .load()
                .migrate();
    }

    // Inserts a minimal, non-duplicate fixture that survives V9's merge untouched. This
    // plan (02-01) only needs a fixture for the V8 assertions and V9's *schema-shape*
    // assertions (join table populated, league_id dropped, sport_id NOT NULL+FK). The
    // exhaustive duplicate-club MERGE scenario (two teams sharing external_id, favourites
    // re-pointing, player de-duplication) is deliberately deferred to plan 02-02, which
    // extends this SAME method with that additional fixture data.
    private static void seedFixtures(JdbcTemplate jdbc) {
        // One sport row — mirrors the real app's seeded "Futbol"/"football" sport.
        jdbc.update("INSERT INTO sport (name, slug) VALUES (?, ?)", "Futbol", "football");
        Long sportId = jdbc.queryForObject(
                "SELECT id FROM sport WHERE slug = ?", Long.class, "football");

        // One league under that sport.
        jdbc.update("INSERT INTO league (sport_id, name) VALUES (?, ?)", sportId, "La Liga");
        seededLeagueId = jdbc.queryForObject(
                "SELECT id FROM league WHERE name = ?", Long.class, "La Liga");

        // One team, with an accented name so we can prove name_normalized strips accents.
        // external_id is left NULL on purpose: V9's merge only groups rows that SHARE a
        // non-null (sport_id, external_id) pair, so a NULL external_id guarantees this
        // row is never treated as a duplicate and is left completely untouched by V9.
        // name_normalized is computed with the SAME TextNormalizer the production code
        // uses (raw JDBC bypasses the entity's @PrePersist hook that would normally do
        // this automatically), so the column round-trips exactly what the real app would
        // have stored.
        String normalizedTeamName = TextNormalizer.normalize(SEEDED_TEAM_RAW_NAME);
        jdbc.update(
                "INSERT INTO team (league_id, name, name_normalized) VALUES (?, ?, ?)",
                seededLeagueId, SEEDED_TEAM_RAW_NAME, normalizedTeamName);
        seededTeamId = jdbc.queryForObject(
                "SELECT id FROM team WHERE name = ?", Long.class, SEEDED_TEAM_RAW_NAME);

        // One player on that team, also with an accented name, also pre-normalized the
        // same way — proves player.name_normalized round-trips too, not just team's.
        String rawPlayerName = "Álvaro Morata";
        String normalizedPlayerName = TextNormalizer.normalize(rawPlayerName);
        jdbc.update(
                "INSERT INTO player (team_id, name, name_normalized) VALUES (?, ?, ?)",
                seededTeamId, rawPlayerName, normalizedPlayerName);
    }

    @Test
    void fullMigrationChain_appliesThroughV9() {
        // GIVEN @BeforeAll already ran the full V1..V9 Flyway chain against this container
        // WHEN we ask Flyway's own bookkeeping table which migrations it recorded as applied
        Integer v9SuccessCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '9' AND success = true",
                Integer.class);
        // THEN version 9 (the last migration file we ship) is recorded as successfully applied —
        // proving the real V1->V9 chain ran end-to-end against real Postgres, not just V8.
        assertThat(v9SuccessCount).isEqualTo(1);
    }

    @Test
    void v8_nameNormalizedColumns_existAndAreIndexed() {
        // GIVEN V8 added name_normalized to both team and player, with a supporting index each
        // WHEN we ask Postgres's own catalog (information_schema / pg_indexes) whether they exist
        Integer teamColCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'team' AND column_name = 'name_normalized'",
                Integer.class);
        Integer playerColCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'player' AND column_name = 'name_normalized'",
                Integer.class);
        Integer teamIdxCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'team' AND indexname = 'idx_team_name_normalized'",
                Integer.class);
        Integer playerIdxCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'player' AND indexname = 'idx_player_name_normalized'",
                Integer.class);
        // THEN both columns and both of their named indexes exist exactly as V8's SQL declares them
        assertThat(teamColCount).isEqualTo(1);
        assertThat(playerColCount).isEqualTo(1);
        assertThat(teamIdxCount).isEqualTo(1);
        assertThat(playerIdxCount).isEqualTo(1);
    }

    @Test
    void v8_nameNormalized_roundTripsTextNormalizerOutput() {
        // GIVEN our fixture inserted "Atlético Madrid" with name_normalized pre-computed
        // via TextNormalizer.normalize() (raw JDBC bypasses the entity's auto-populating
        // @PrePersist hook, so this test explicitly proves the COLUMN can correctly hold
        // that computed value — it does NOT exercise the separate NameNormalizationBackfill
        // ApplicationRunner bean, which needs a JPA/Spring context and is out of this
        // plain-JUnit5 IT's scope; that class would need its own focused unit test).
        // WHEN we read back the stored name_normalized value for that seeded team
        String storedNormalized = jdbc.queryForObject(
                "SELECT name_normalized FROM team WHERE id = ?", String.class, seededTeamId);
        // THEN it matches exactly what TextNormalizer.normalize() computes for the raw name
        // — e.g. "Atlético Madrid" -> "atletico madrid" (accents stripped, lower-cased).
        assertThat(storedNormalized).isEqualTo(TextNormalizer.normalize(SEEDED_TEAM_RAW_NAME));
        assertThat(storedNormalized).isEqualTo("atletico madrid");
    }

    @Test
    void v9_teamLeagueIdColumn_isDropped() {
        // GIVEN V9's final step is "ALTER TABLE team DROP COLUMN league_id" (replaced by
        // the new team_league join table)
        // WHEN we ask the catalog whether team.league_id still exists
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'team' AND column_name = 'league_id'",
                Integer.class);
        // THEN it does not — zero matching columns
        assertThat(count).isZero();
    }

    @Test
    void v9_teamSportId_isNotNull_andForeignKeyed() {
        // GIVEN V9 adds team.sport_id as NOT NULL with an explicitly-named FK (fk_team_sport)
        // WHEN we inspect the column's nullability and look for that named constraint
        String nullable = jdbc.queryForObject(
                "SELECT is_nullable FROM information_schema.columns WHERE table_name = 'team' AND column_name = 'sport_id'",
                String.class);
        Integer fkCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_name = 'fk_team_sport'",
                Integer.class);
        // THEN sport_id rejects NULLs, and the fk_team_sport foreign key exists (this
        // constraint IS explicitly named in V9's own SQL, so asserting the literal name is
        // safe — unlike Postgres's auto-generated names for unnamed UNIQUE constraints).
        assertThat(nullable).isEqualTo("NO");
        assertThat(fkCount).isEqualTo(1);
    }

    @Test
    void v9_teamLeagueJoinTable_existsAndPopulated() {
        // GIVEN V9 creates team_league(team_id, league_id) and backfills it from every
        // team's old (pre-drop) league_id column
        // WHEN we check the table exists and carries our seeded team's link to its league
        Integer tableCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'team_league'",
                Integer.class);
        Integer linkCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM team_league WHERE team_id = ? AND league_id = ?",
                Integer.class, seededTeamId, seededLeagueId);
        // THEN the join table exists, and it carries the exact (team, league) pair we
        // seeded before V9 ran — proving the backfill from the old league_id column worked.
        assertThat(tableCount).isEqualTo(1);
        assertThat(linkCount).isEqualTo(1);
    }
}
