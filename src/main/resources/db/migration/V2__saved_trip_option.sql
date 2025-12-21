-- Add table for saved trip options
CREATE TABLE IF NOT EXISTS saved_trip_option (
    id UUID PRIMARY KEY,
    client_id VARCHAR(128) NOT NULL,
    search_id UUID,
    trip_option_id UUID,
    origin VARCHAR(16),
    destination VARCHAR(16),
    total_price NUMERIC(19,2),
    currency VARCHAR(8),
    airline VARCHAR(128),
    hotel_name VARCHAR(255),
    value_score DOUBLE PRECISION,
    created_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_saved_client_created ON saved_trip_option (client_id, created_at DESC);
