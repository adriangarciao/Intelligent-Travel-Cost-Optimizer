-- V11: Convert flight_option duration columns from INTERVAL to NUMERIC(21,0)
--
-- Hibernate maps java.time.Duration to NUMERIC(21,0) (nanoseconds) by default,
-- but V1/V6 created these columns as PostgreSQL INTERVAL. Inserts failed with
-- SQLState 42804: "column \"duration\" is of type interval but expression is of
-- type numeric". The USING clause converts any existing interval values to
-- nanoseconds so the change is safe on databases with data.

ALTER TABLE flight_option
  ALTER COLUMN duration TYPE numeric(21,0)
  USING (EXTRACT(EPOCH FROM duration) * 1000000000)::numeric(21,0);

ALTER TABLE flight_option
  ALTER COLUMN return_duration TYPE numeric(21,0)
  USING (EXTRACT(EPOCH FROM return_duration) * 1000000000)::numeric(21,0);
