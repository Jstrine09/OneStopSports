-- V8: Add name_normalized to the team and player tables.
--
-- This stores an accent-stripped, lower-cased copy of each name so the global
-- search can be accent-insensitive. Before this, search used a plain
-- case-insensitive LIKE on `name`, so "Dembele" would never match the stored
-- "Dembélé" and "Atletico" would miss "Atlético".
--
-- How the column stays correct:
--   The Team and Player JPA entities have a @PrePersist/@PreUpdate hook that
--   computes this value from `name` on every insert and update, so it can never
--   drift out of sync. Accent-stripping needs Java's Unicode normalizer, which
--   plain SQL can't replicate (it would require the pg_trgm/unaccent extension),
--   so we leave existing rows null here and let the boot-time backfill
--   (NameNormalizationBackfill) populate them on the next startup.
--
-- Nullable on purpose: pre-existing rows start null and get backfilled at boot.

ALTER TABLE team   ADD COLUMN name_normalized VARCHAR(255);
ALTER TABLE player ADD COLUMN name_normalized VARCHAR(255);

-- Indexes to speed up the search lookups against the normalized column.
CREATE INDEX idx_team_name_normalized   ON team(name_normalized);
CREATE INDEX idx_player_name_normalized ON player(name_normalized);
