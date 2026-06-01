-- V7: Add external_id to the team table.
--
-- This stores the upstream provider's ID for each team:
--   NBA teams  → ESPN string team ID (e.g. "1" for Atlanta Hawks)
--   NFL teams  → ESPN string team ID (e.g. "22" for New England Patriots)
--   Football   → football-data.org integer team ID (stored as a string for uniformity)
--
-- Why we need this:
--   The data loaders fetch teams from external APIs (ESPN, football-data.org) and
--   use the provider ID to then fetch rosters. Previously that ID was held in memory
--   only during the seed run and then discarded. Persisting it lets us:
--     1. Call ESPN's historical roster endpoint: GET /teams/{espnId}/roster?season=YYYY
--     2. Avoid a full re-fetch of all teams just to resolve an ID at runtime.
--
-- Nullable: pre-V7 rows will have null; the data loaders detect and backfill on restart.

ALTER TABLE team ADD COLUMN external_id VARCHAR(50);

-- Index for fast lookup when routing historical roster requests.
CREATE INDEX idx_team_external_id ON team(external_id);
