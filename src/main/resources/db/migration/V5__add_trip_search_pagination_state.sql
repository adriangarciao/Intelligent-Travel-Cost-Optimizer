-- Add pagination state columns to trip_search for progressive flight fetching
ALTER TABLE trip_search ADD COLUMN IF NOT EXISTS flight_fetch_limit INT DEFAULT 0;
ALTER TABLE trip_search ADD COLUMN IF NOT EXISTS flight_exhausted BOOLEAN DEFAULT FALSE;
