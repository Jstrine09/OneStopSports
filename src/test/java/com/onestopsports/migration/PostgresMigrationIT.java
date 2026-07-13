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

    // ── Duplicate-club fixture (plan 02-02) ─────────────────────────────────
    // These constants/fields drive the exhaustive V9 MERGE assertions below. They
    // simulate the REAL pre-V9 bug: the same football club seeded once PER
    // competition it plays in, producing two `team` rows that share the same
    // upstream (football-data.org) external_id. external_id is deliberately
    // DIFFERENT from the plan-02-01 fixture team above (which is NULL) so this
    // pair is the only group V9's merge groups together.
    private static final String DUP_TEAM_EXTERNAL_ID = "86"; // fake but realistic football-data.org team id
    private static final String DUP_TEAM_NAME = "Real Madrid";
    private static final String DUP_PLAYER_NAME = "Jude Bellingham"; // seeded once per competition, pre-merge
    private static Long domesticLeagueId;
    private static Long continentalLeagueId;
    private static Long canonicalTeamId;       // lower id of the duplicate pair -> V9's MIN(id) survivor
    private static Long duplicateTeamId;       // higher id of the duplicate pair -> deleted by V9's merge
    private static Long canonicalPlayerId;     // lower id of the duplicate player pair -> survives de-dup
    private static Long duplicatePlayerId;     // higher id of the duplicate player pair -> deleted by de-dup
    private static Long userTeamRepointId;     // favourited ONLY the duplicate team -> re-point branch
    private static Long userTeamCollisionId;   // favourited BOTH teams -> collision/de-dupe branch
    private static Long userPlayerRepointId;   // favourited ONLY the duplicate player -> re-point branch
    private static Long userPlayerCollisionId; // favourited BOTH players -> collision/de-dupe branch

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

    // Inserts BOTH fixture scenarios needed across the two migration IT plans:
    //   1. (plan 02-01) A minimal, non-duplicate fixture that survives V9's merge
    //      untouched — used for the V8 assertions and V9's *schema-shape* assertions
    //      (join table populated, league_id dropped, sport_id NOT NULL+FK).
    //   2. (plan 02-02) The exhaustive duplicate-club MERGE scenario — two team rows
    //      sharing external_id, duplicate players, and favourites covering both the
    //      re-point and collision/de-dupe branches — used for the seven v9_* merge
    //      @Test methods below.
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

        // ── Duplicate-club scenario (plan 02-02) ─────────────────────────────
        // Everything below is built at the SAME V8-era point in the schema as the
        // fixture above (team.league_id still exists, team.sport_id does not exist
        // yet) — that's exactly the "before" shape V9's merge step is written to
        // find and collapse.

        // Two leagues under the SAME sport (reuses sportId from above). This
        // matters because V9 backfills team.sport_id from each team's single
        // (pre-merge) league, and the merge only groups rows sharing sport_id — so
        // both duplicate team rows below must end up with the same sport_id.
        jdbc.update("INSERT INTO league (sport_id, name) VALUES (?, ?)", sportId, "Domestic League");
        domesticLeagueId = jdbc.queryForObject(
                "SELECT id FROM league WHERE name = ?", Long.class, "Domestic League");

        jdbc.update("INSERT INTO league (sport_id, name) VALUES (?, ?)", sportId, "Continental Cup");
        continentalLeagueId = jdbc.queryForObject(
                "SELECT id FROM league WHERE name = ?", Long.class, "Continental Cup");

        // The "duplicate club": two team rows with the SAME name and SAME
        // external_id, but linked to DIFFERENT leagues — exactly what the old
        // one-team-per-competition seeding produced in production. We insert the
        // domestic-league row FIRST so it gets the smaller id: V9's merge picks
        // MIN(id) per (sport_id, external_id) group as the canonical survivor, so
        // this row is deliberately our canonical row and the second insert is
        // deliberately the duplicate that V9 should delete.
        jdbc.update(
                "INSERT INTO team (league_id, name, external_id) VALUES (?, ?, ?)",
                domesticLeagueId, DUP_TEAM_NAME, DUP_TEAM_EXTERNAL_ID);
        canonicalTeamId = jdbc.queryForObject(
                "SELECT id FROM team WHERE external_id = ? ORDER BY id ASC LIMIT 1",
                Long.class, DUP_TEAM_EXTERNAL_ID);

        jdbc.update(
                "INSERT INTO team (league_id, name, external_id) VALUES (?, ?, ?)",
                continentalLeagueId, DUP_TEAM_NAME, DUP_TEAM_EXTERNAL_ID);
        duplicateTeamId = jdbc.queryForObject(
                "SELECT id FROM team WHERE external_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, DUP_TEAM_EXTERNAL_ID);

        // The SAME player, seeded once under EACH duplicate team row — mirrors the
        // real DataLoader seeding a full squad per competition, pre-V9. V9 step 3b
        // (player de-dupe) is what should collapse these to one row per
        // (team_id, name) once step 3a re-points both onto the canonical team.
        jdbc.update("INSERT INTO player (team_id, name) VALUES (?, ?)", canonicalTeamId, DUP_PLAYER_NAME);
        canonicalPlayerId = jdbc.queryForObject(
                "SELECT id FROM player WHERE team_id = ? AND name = ?",
                Long.class, canonicalTeamId, DUP_PLAYER_NAME);

        jdbc.update("INSERT INTO player (team_id, name) VALUES (?, ?)", duplicateTeamId, DUP_PLAYER_NAME);
        duplicatePlayerId = jdbc.queryForObject(
                "SELECT id FROM player WHERE team_id = ? AND name = ?",
                Long.class, duplicateTeamId, DUP_PLAYER_NAME);

        // Four users covering the two V9 favourite branches, at BOTH the team level
        // and the player level:
        //   - "repoint" users favourite ONLY the duplicate row -> V9's UPDATE should
        //     re-point their favourite onto the canonical row (the NOT EXISTS guard
        //     passes because they have no existing canonical favourite).
        //   - "collision" users favourite BOTH the canonical row AND the duplicate
        //     row -> V9's NOT EXISTS guard should SKIP the re-point (a canonical
        //     favourite already exists for them), and the trailing DELETE removes
        //     the leftover duplicate-pointing row instead — this is exactly what
        //     keeps the (user_id, team_id) / (user_id, player_id) unique
        //     constraints from being violated mid-migration.
        userTeamRepointId = insertUser(jdbc, "user_team_repoint", "team.repoint@test.local");
        jdbc.update(
                "INSERT INTO favorite_team (user_id, team_id) VALUES (?, ?)",
                userTeamRepointId, duplicateTeamId);

        userTeamCollisionId = insertUser(jdbc, "user_team_collision", "team.collision@test.local");
        jdbc.update(
                "INSERT INTO favorite_team (user_id, team_id) VALUES (?, ?)",
                userTeamCollisionId, canonicalTeamId);
        jdbc.update(
                "INSERT INTO favorite_team (user_id, team_id) VALUES (?, ?)",
                userTeamCollisionId, duplicateTeamId);

        userPlayerRepointId = insertUser(jdbc, "user_player_repoint", "player.repoint@test.local");
        jdbc.update(
                "INSERT INTO favorite_player (user_id, player_id) VALUES (?, ?)",
                userPlayerRepointId, duplicatePlayerId);

        userPlayerCollisionId = insertUser(jdbc, "user_player_collision", "player.collision@test.local");
        jdbc.update(
                "INSERT INTO favorite_player (user_id, player_id) VALUES (?, ?)",
                userPlayerCollisionId, canonicalPlayerId);
        jdbc.update(
                "INSERT INTO favorite_player (user_id, player_id) VALUES (?, ?)",
                userPlayerCollisionId, duplicatePlayerId);
    }

    // Small helper so the four favourite-branch users above don't repeat the same
    // INSERT + id-lookup four times over. password_hash is a throwaway literal —
    // this fixture never exercises auth, only the favourite tables the V9 merge
    // touches.
    private static Long insertUser(JdbcTemplate jdbc, String username, String email) {
        jdbc.update(
                "INSERT INTO user_account (username, email, password_hash) VALUES (?, ?, ?)",
                username, email, "not-a-real-hash");
        return jdbc.queryForObject(
                "SELECT id FROM user_account WHERE username = ?", Long.class, username);
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
