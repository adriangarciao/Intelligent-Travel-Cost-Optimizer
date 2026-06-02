-- Fix NULL values in primitive columns that cause Hibernate mapping errors
-- These columns were added with defaults but existing rows may have NULL values

UPDATE trip_search SET flight_fetch_limit = 0 WHERE flight_fetch_limit IS NULL;
UPDATE trip_search SET flight_exhausted = FALSE WHERE flight_exhausted IS NULL;
UPDATE trip_search SET num_travelers = 1 WHERE num_travelers IS NULL;

-- Set default values for future inserts (PostgreSQL syntax)
ALTER TABLE trip_search ALTER COLUMN flight_fetch_limit SET DEFAULT 0;
ALTER TABLE trip_search ALTER COLUMN flight_exhausted SET DEFAULT FALSE;
ALTER TABLE trip_search ALTER COLUMN num_travelers SET DEFAULT 1;

-- Add NOT NULL constraints now that data is cleaned up
ALTER TABLE trip_search ALTER COLUMN flight_fetch_limit SET NOT NULL;
ALTER TABLE trip_search ALTER COLUMN flight_exhausted SET NOT NULL;
