-- Add option_json column to saved_offer to store full TripOption payload
ALTER TABLE IF EXISTS saved_offer
  ADD COLUMN IF NOT EXISTS option_json TEXT;

-- Also ensure saved_offer exists (defensive)
CREATE TABLE IF NOT EXISTS saved_offer (
  id uuid PRIMARY KEY
);
