CREATE TABLE price_observation (
    id UUID NOT NULL,
    origin VARCHAR(16) NOT NULL,
    destination VARCHAR(16) NOT NULL,
    departure_date DATE NOT NULL,
    observed_price NUMERIC(19, 2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_price_observation PRIMARY KEY (id)
);

CREATE INDEX idx_price_obs_route ON price_observation (origin, destination, departure_date);
CREATE INDEX idx_price_obs_created ON price_observation (created_at);
