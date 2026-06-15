-- V9: Make a club a single row that can belong to MANY competitions.
--
-- Before this migration the team↔league relationship was one-to-many: a club was
-- seeded once PER competition it played in, so e.g. Real Madrid existed as two
-- separate `team` rows (one under La Liga, one under the Champions League), each with
-- its own full squad of duplicated `player` rows. Search papered over this with a
-- presentation-layer collapse-by-name; this migration fixes it structurally.
--
-- What it does, in order:
--   1. Create the `team_league` join table and backfill it from the existing team.league_id.
--   2. Add `team.sport_id` (a club belongs to exactly one sport) and backfill it.
--   3. MERGE duplicate clubs: collapse rows that share (sport_id, external_id) into one
--      canonical row, re-pointing players, league links and favourites, then delete the
--      duplicates. Also de-duplicate the players that the merge brings onto a shared club.
--   4. Drop the now-unused team.league_id column.
--
-- The merge key is (sport_id, external_id): the upstream provider's team id
-- (football-data.org team id) is identical across every competition a club appears in,
-- which is exactly what caused the duplication — so it's the reliable way to find the
-- duplicates. NBA/NFL franchises each play in a single league, so they have no duplicates
-- and are untouched. Rows with a NULL external_id (legacy seeds) are left as-is.
--
-- This migration runs against PostgreSQL only — Flyway is disabled in the H2 test profile,
-- where Hibernate builds the schema directly from the entity mapping instead.

-- ── 1. team↔league join table ───────────────────────────────────────────────
CREATE TABLE team_league (
    team_id   BIGINT NOT NULL REFERENCES team(id)   ON DELETE CASCADE,
    league_id BIGINT NOT NULL REFERENCES league(id) ON DELETE CASCADE,
    PRIMARY KEY (team_id, league_id)
);

-- The primary key indexes (team_id, league_id); add the reverse index for the
-- "all teams in a league" lookup (findByLeagues_Id), which filters on league_id.
CREATE INDEX idx_team_league_league ON team_league(league_id);

-- Backfill: every existing team currently has exactly one league via team.league_id.
INSERT INTO team_league (team_id, league_id)
SELECT id, league_id FROM team;

-- ── 2. Direct sport link on team ────────────────────────────────────────────
ALTER TABLE team ADD COLUMN sport_id BIGINT;

-- Backfill the sport from each team's (single, pre-merge) league.
UPDATE team t
SET    sport_id = l.sport_id
FROM   league l
WHERE  t.league_id = l.id;

ALTER TABLE team ALTER COLUMN sport_id SET NOT NULL;
ALTER TABLE team ADD CONSTRAINT fk_team_sport FOREIGN KEY (sport_id) REFERENCES sport(id);
CREATE INDEX idx_team_sport ON team(sport_id);

-- ── 3a. Merge duplicate clubs ────────────────────────────────────────────────
-- Map each duplicate (non-canonical) team to the canonical team it should collapse into.
-- Canonical = the smallest id in each (sport_id, external_id) group that has >1 row.
CREATE TEMP TABLE team_merge_map AS
SELECT t.id AS dup_id, c.canonical_id
FROM   team t
JOIN (
    SELECT sport_id, external_id, MIN(id) AS canonical_id
    FROM   team
    WHERE  external_id IS NOT NULL
    GROUP  BY sport_id, external_id
    HAVING COUNT(*) > 1
) c ON t.sport_id = c.sport_id AND t.external_id = c.external_id
WHERE t.id <> c.canonical_id;

-- Re-point players from the duplicate clubs onto the canonical club. (This may create
-- duplicate players on the canonical club — the same squad was seeded per competition —
-- which step 3b then de-duplicates.)
UPDATE player p
SET    team_id = m.canonical_id
FROM   team_merge_map m
WHERE  p.team_id = m.dup_id;

-- Re-point the duplicates' league links onto the canonical club, skipping any that would
-- collide with a link the canonical club already has, then drop the duplicates' links.
INSERT INTO team_league (team_id, league_id)
SELECT DISTINCT m.canonical_id, tl.league_id
FROM   team_league tl
JOIN   team_merge_map m ON tl.team_id = m.dup_id
ON CONFLICT DO NOTHING;

DELETE FROM team_league tl
USING  team_merge_map m
WHERE  tl.team_id = m.dup_id;

-- Re-point favourites onto the canonical club, but only where the user hasn't already
-- favourited the canonical club (the (user_id, team_id) unique constraint), then drop the rest.
UPDATE favorite_team ft
SET    team_id = m.canonical_id
FROM   team_merge_map m
WHERE  ft.team_id = m.dup_id
  AND  NOT EXISTS (
        SELECT 1 FROM favorite_team ft2
        WHERE ft2.user_id = ft.user_id AND ft2.team_id = m.canonical_id
  );

DELETE FROM favorite_team ft
USING  team_merge_map m
WHERE  ft.team_id = m.dup_id;

-- The duplicate club rows are now fully orphaned — delete them.
DELETE FROM team t
USING  team_merge_map m
WHERE  t.id = m.dup_id;

-- ── 3b. De-duplicate players on the merged clubs ─────────────────────────────
-- After 3a a canonical club may carry the same player many times (one per competition the
-- club used to be duplicated across). Collapse to one row per (team_id, name), keeping the
-- smallest id.
CREATE TEMP TABLE player_merge_map AS
SELECT p.id AS dup_id, c.canonical_id
FROM   player p
JOIN (
    SELECT team_id, name, MIN(id) AS canonical_id
    FROM   player
    GROUP  BY team_id, name
    HAVING COUNT(*) > 1
) c ON p.team_id = c.team_id AND p.name = c.name
WHERE p.id <> c.canonical_id;

-- Re-point favourited players onto the canonical player row (respecting the unique
-- (user_id, player_id) constraint), then drop the duplicate favourites.
UPDATE favorite_player fp
SET    player_id = m.canonical_id
FROM   player_merge_map m
WHERE  fp.player_id = m.dup_id
  AND  NOT EXISTS (
        SELECT 1 FROM favorite_player fp2
        WHERE fp2.user_id = fp.user_id AND fp2.player_id = m.canonical_id
  );

DELETE FROM favorite_player fp
USING  player_merge_map m
WHERE  fp.player_id = m.dup_id;

DELETE FROM player p
USING  player_merge_map m
WHERE  p.id = m.dup_id;

-- ── 4. Drop the old single-league column ─────────────────────────────────────
-- The team↔league relationship now lives entirely in team_league; team.league_id is gone.
ALTER TABLE team DROP COLUMN league_id;
