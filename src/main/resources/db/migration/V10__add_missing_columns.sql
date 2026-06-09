-- V10: Add columns that exist in JPA entities but were never migrated

-- trip_option: ml_recommendation was added to the entity without a migration
ALTER TABLE trip_option ADD COLUMN IF NOT EXISTS ml_recommendation TEXT;

-- saved_offer: the entity was expanded far beyond what V3+V4 created
ALTER TABLE saved_offer ADD COLUMN IF NOT EXISTS client_id VARCHAR(255);
ALTER TABLE saved_offer ADD COLUMN IF NOT EXISTS trip_option_id UUID;
ALTER TABLE saved_offer ADD COLUMN IF NOT EXISTS origin VARCHAR(16);
ALTER TABLE saved_offer ADD COLUMN IF NOT EXISTS destination VARCHAR(16);
ALTER TABLE saved_offer ADD COLUMN IF NOT EXISTS depart_date VARCHAR(255);
ALTER TABLE saved_offer ADD COLUMN IF NOT EXISTS return_date VARCHAR(255);
ALTER TABLE saved_offer ADD COLUMN IF NOT EXISTS total_price NUMERIC(19, 2);
ALTER TABLE saved_offer ADD COLUMN IF NOT EXISTS currency VARCHAR(8);
ALTER TABLE saved_offer ADD COLUMN IF NOT EXISTS airline_code VARCHAR(16);
ALTER TABLE saved_offer ADD COLUMN IF NOT EXISTS airline_name VARCHAR(128);
ALTER TABLE saved_offer ADD COLUMN IF NOT EXISTS flight_number VARCHAR(128);
ALTER TABLE saved_offer ADD COLUMN IF NOT EXISTS duration_text VARCHAR(64);
ALTER TABLE saved_offer ADD COLUMN IF NOT EXISTS segments TEXT;
ALTER TABLE saved_offer ADD COLUMN IF NOT EXISTS note VARCHAR(1024);
ALTER TABLE saved_offer ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE;

-- Index that V3 attempted but couldn't create (columns didn't exist yet)
CREATE INDEX IF NOT EXISTS idx_saved_offer_client_created ON saved_offer (client_id, created_at DESC);

-- feedback_event: airline_code column is VARCHAR(3) in V7 but entity declares length=8
ALTER TABLE feedback_event ALTER COLUMN airline_code TYPE VARCHAR(8);
