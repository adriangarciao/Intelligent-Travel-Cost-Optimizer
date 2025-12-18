-- Flyway V1: initial schema for traveloptimizer
-- Creates core tables: trip_search, trip_option, flight_option, lodging_option

CREATE TABLE IF NOT EXISTS flight_option (
    id uuid PRIMARY KEY,
    airline varchar(255),
    flight_number varchar(128),
    stops integer,
    duration varchar(64),
    price numeric(12,2)
);

CREATE TABLE IF NOT EXISTS lodging_option (
    id uuid PRIMARY KEY,
    hotel_name varchar(255),
    lodging_type varchar(128),
    rating double precision,
    price_per_night numeric(12,2),
    nights integer
);

CREATE TABLE IF NOT EXISTS trip_search (
    id uuid PRIMARY KEY,
    origin varchar(12),
    destination varchar(12),
    earliest_departure_date date,
    latest_departure_date date,
    earliest_return_date date,
    latest_return_date date,
    max_budget numeric(12,2),
    num_travelers integer,
    created_at timestamp
);

CREATE TABLE IF NOT EXISTS trip_option (
    id uuid PRIMARY KEY,
    trip_search_id uuid NOT NULL,
    total_price numeric(12,2),
    currency varchar(12),
    value_score double precision,
    flight_option_id uuid,
    lodging_option_id uuid,
    CONSTRAINT fk_trip_search FOREIGN KEY (trip_search_id) REFERENCES trip_search(id) ON DELETE CASCADE,
    CONSTRAINT fk_flight_option FOREIGN KEY (flight_option_id) REFERENCES flight_option(id) ON DELETE SET NULL,
    CONSTRAINT fk_lodging_option FOREIGN KEY (lodging_option_id) REFERENCES lodging_option(id) ON DELETE SET NULL
);

-- Element collection for flight segments
CREATE TABLE IF NOT EXISTS flight_option_segments (
    flight_option_id uuid NOT NULL,
    segment varchar(1024),
    CONSTRAINT fk_fos_flight FOREIGN KEY (flight_option_id) REFERENCES flight_option(id) ON DELETE CASCADE
);
