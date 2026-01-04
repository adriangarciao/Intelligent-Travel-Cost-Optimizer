-- Add round-trip support columns to trip_search and flight_option

-- trip_search: Add trip type and selected dates
ALTER TABLE trip_search ADD COLUMN IF NOT EXISTS trip_type VARCHAR(20) DEFAULT 'ONE_WAY';
ALTER TABLE trip_search ADD COLUMN IF NOT EXISTS selected_departure_date DATE;
ALTER TABLE trip_search ADD COLUMN IF NOT EXISTS selected_return_date DATE;

-- flight_option: Add departure date column
ALTER TABLE flight_option ADD COLUMN IF NOT EXISTS departure_date DATE;

-- flight_option: Add airline code and name columns (if not already present)
ALTER TABLE flight_option ADD COLUMN IF NOT EXISTS airline_code VARCHAR(10);
ALTER TABLE flight_option ADD COLUMN IF NOT EXISTS airline_name VARCHAR(255);

-- flight_option: Add return flight columns for round-trip
ALTER TABLE flight_option ADD COLUMN IF NOT EXISTS return_airline VARCHAR(255);
ALTER TABLE flight_option ADD COLUMN IF NOT EXISTS return_airline_code VARCHAR(10);
ALTER TABLE flight_option ADD COLUMN IF NOT EXISTS return_airline_name VARCHAR(255);
ALTER TABLE flight_option ADD COLUMN IF NOT EXISTS return_flight_number VARCHAR(64);
ALTER TABLE flight_option ADD COLUMN IF NOT EXISTS return_stops INTEGER;
ALTER TABLE flight_option ADD COLUMN IF NOT EXISTS return_duration INTERVAL;
ALTER TABLE flight_option ADD COLUMN IF NOT EXISTS return_date DATE;

-- Create table for return flight segments (ElementCollection)
CREATE TABLE IF NOT EXISTS flight_option_return_segments (
  flight_option_id UUID NOT NULL,
  segment TEXT,
  CONSTRAINT fk_fors_flight_option FOREIGN KEY (flight_option_id) REFERENCES flight_option(id) ON DELETE CASCADE
);

-- Create index for efficient lookup of return segments by flight option
CREATE INDEX IF NOT EXISTS idx_flight_option_return_segments_id ON flight_option_return_segments(flight_option_id);
