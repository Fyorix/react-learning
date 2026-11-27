CREATE TABLE carpool_trips (
    id           UUID PRIMARY KEY,
    driver_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    from_city    VARCHAR(100) NOT NULL,
    to_city      VARCHAR(100) NOT NULL,
    departure_at TIMESTAMPTZ  NOT NULL,
    seats        INT          NOT NULL CHECK (seats BETWEEN 1 AND 8),
    comment      VARCHAR(500),
    status       VARCHAR(20)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL
);

CREATE TABLE carpool_bookings (
    id           UUID PRIMARY KEY,
    trip_id      UUID        NOT NULL REFERENCES carpool_trips (id) ON DELETE CASCADE,
    passenger_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_carpool_booking UNIQUE (trip_id, passenger_id)
);

CREATE INDEX idx_carpool_trips_departure ON carpool_trips (departure_at);
