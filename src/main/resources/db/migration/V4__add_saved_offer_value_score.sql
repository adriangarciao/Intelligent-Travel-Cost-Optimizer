-- Add value_score column to saved_offer to persist original TripOption value score
ALTER TABLE saved_offer
ADD COLUMN value_score double precision;