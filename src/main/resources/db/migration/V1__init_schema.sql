-- Initial schema for Intelligent Travel Cost Optimizer
-- Note: keep migrations additive. Use Flyway to evolve schema.

CREATE TABLE IF NOT EXISTS trip_search (
    id UUID PRIMARY KEY,
    origin VARCHAR(16) NOT NULL,
    destination VARCHAR(16) NOT NULL,
    earliest_departure_date DATE,
    latest_departure_date DATE,
    earliest_return_date DATE,
    latest_return_date DATE,
    max_budget NUMERIC(19,2),
    num_travelers INTEGER,
    created_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS flight_option (
    id UUID PRIMARY KEY,
    airline VARCHAR(128),
    flight_number VARCHAR(64),
    stops INTEGER,
    duration BIGINT,
    price NUMERIC(19,2)
);

CREATE TABLE IF NOT EXISTS lodging_option (
    id UUID PRIMARY KEY,
    hotel_name VARCHAR(255),
    lodging_type VARCHAR(64),
    rating DOUBLE PRECISION,
    price_per_night NUMERIC(19,2),
    nights INTEGER
);

CREATE TABLE IF NOT EXISTS trip_option (
    id UUID PRIMARY KEY,
    total_price NUMERIC(19,2),
    currency VARCHAR(8),
    value_score DOUBLE PRECISION,
    trip_search_id UUID REFERENCES trip_search(id),
    flight_option_id UUID REFERENCES flight_option(id),
    lodging_option_id UUID REFERENCES lodging_option(id)
);
