-- Stores an external API identifier on each player row.
--
-- The meaning of this column depends on the player's sport:
--   • basketball         → ESPN athlete ID         (e.g. "1966" for LeBron James)
--   • american-football  → ESPN athlete ID         (e.g. "3139477" for Patrick Mahomes)
--   • football (soccer)  → API-Football player ID  (populated lazily on first stats lookup)
--
-- We need this because the career stats endpoints on those external APIs key on
-- their own athlete/player IDs, not on our auto-generated DB primary keys.
--
-- VARCHAR(64) keeps the column flexible: ESPN IDs are short numeric strings,
-- but storing them as text avoids assumptions about future APIs that may use
-- alphanumeric IDs (e.g. balldontlie uses ints, FBref uses 8-char hex slugs).
ALTER TABLE player ADD COLUMN external_id VARCHAR(64);

-- Non-unique index — lookups go DB-id → external-id, not the reverse,
-- but the index speeds up the rare reverse lookup we use during re-seeding
-- to spot rows that still need backfilling.
CREATE INDEX idx_player_external_id ON player(external_id);
