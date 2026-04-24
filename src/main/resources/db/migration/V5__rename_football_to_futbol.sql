-- Rename the Football sport to Futbol so it's clearly distinct from
-- American Football (NFL), which will be added as a separate sport.
-- The slug stays 'football' — changing it would break existing URL routes
-- (e.g. /api/sports/football/leagues) and frontend sport selectors.
UPDATE sport SET name = 'Futbol' WHERE slug = 'football';
