-- Initial schema for traveloptimizer
-- Creates tables used by JPA entities

CREATE TABLE IF NOT EXISTS trip_search (
  id uuid NOT NULL PRIMARY KEY,
  origin varchar(16) NOT NULL,
  destination varchar(16) NOT NULL,
  earliest_departure_date date,
  latest_departure_date date,
  earliest_return_date date,
  latest_return_date date,
  max_budget numeric(19,2),
  num_travelers integer,
  created_at timestamp with time zone
);

CREATE TABLE IF NOT EXISTS flight_option (
  id uuid NOT NULL PRIMARY KEY,
  airline varchar(255),
  flight_number varchar(64),
  stops integer,
  duration interval,
  price numeric(19,2)
);

CREATE TABLE IF NOT EXISTS flight_option_segments (
  flight_option_id uuid NOT NULL,
  segment text,
  CONSTRAINT fk_fos_flight_option FOREIGN KEY (flight_option_id) REFERENCES flight_option(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS lodging_option (
  id uuid NOT NULL PRIMARY KEY,
  hotel_name varchar(255),
  lodging_type varchar(64),
  rating double precision,
  price_per_night numeric(19,2),
  nights integer
);

CREATE TABLE IF NOT EXISTS trip_option (
  id uuid NOT NULL PRIMARY KEY,
  total_price numeric(19,2),
  currency varchar(8),
  value_score double precision,
  trip_search_id uuid,
  flight_option_id uuid,
  lodging_option_id uuid,
  CONSTRAINT fk_trip_search FOREIGN KEY (trip_search_id) REFERENCES trip_search(id) ON DELETE CASCADE,
  CONSTRAINT fk_trip_flight FOREIGN KEY (flight_option_id) REFERENCES flight_option(id) ON DELETE SET NULL,
  CONSTRAINT fk_trip_lodging FOREIGN KEY (lodging_option_id) REFERENCES lodging_option(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS saved_trip_option (
  id uuid NOT NULL PRIMARY KEY,
  client_id varchar(255) NOT NULL,
  search_id uuid,
  trip_option_id uuid,
  origin varchar(16),
  destination varchar(16),
  total_price numeric(19,2),
  currency varchar(8),
  airline varchar(128),
  hotel_name varchar(255),
  value_score double precision,
  created_at timestamp with time zone
);
