-- Consolidated initial schema for Intelligent Travel Cost Optimizer

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
    trip_search_id UUID NOT NULL,
    flight_option_id UUID,
    lodging_option_id UUID,
    CONSTRAINT fk_trip_search FOREIGN KEY (trip_search_id) REFERENCES trip_search(id) ON DELETE CASCADE,
    CONSTRAINT fk_flight_option FOREIGN KEY (flight_option_id) REFERENCES flight_option(id) ON DELETE SET NULL,
    CONSTRAINT fk_lodging_option FOREIGN KEY (lodging_option_id) REFERENCES lodging_option(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS flight_option_segments (
    flight_option_id UUID NOT NULL,
    segment VARCHAR(1024),
    CONSTRAINT fk_fos_flight FOREIGN KEY (flight_option_id) REFERENCES flight_option(id) ON DELETE CASCADE
);
