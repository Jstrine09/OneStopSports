-- Stores the football-data.org competition ID on each league row.
-- This lets the app map a DB league back to the external API without a separate lookup table.
ALTER TABLE league ADD COLUMN external_id INTEGER;

-- Seed known values for leagues already in the database
UPDATE league SET external_id = 2021 WHERE name = 'Premier League';
UPDATE league SET external_id = 2014 WHERE name = 'Primera Division';
UPDATE league SET external_id = 2002 WHERE name = 'Bundesliga';
