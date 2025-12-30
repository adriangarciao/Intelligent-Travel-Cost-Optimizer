-- Add option_json column to saved_offer to store full TripOption payload
ALTER TABLE IF EXISTS saved_offer
  ADD COLUMN IF NOT EXISTS option_json TEXT;

-- Also ensure saved_offer exists (defensive)
CREATE TABLE IF NOT EXISTS saved_offer (
  id uuid PRIMARY KEY
);

-- Index for client_id if not exists
CREATE INDEX IF NOT EXISTS idx_saved_offer_client_created ON saved_offer (client_id, created_at DESC);
