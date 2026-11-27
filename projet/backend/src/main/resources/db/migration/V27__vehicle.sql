-- 5.26 Véhicules de service
CREATE TABLE vehicles (
    id                   UUID PRIMARY KEY,
    plate                VARCHAR(12)  NOT NULL UNIQUE,
    model                VARCHAR(80)  NOT NULL,
    seats                INT          NOT NULL CHECK (seats BETWEEN 1 AND 9),
    license_type         VARCHAR(2)   NOT NULL,
    status               VARCHAR(12)  NOT NULL,
    mileage              INT          NOT NULL CHECK (mileage >= 0),
    last_service_mileage INT          NOT NULL CHECK (last_service_mileage >= 0),
    created_at           TIMESTAMPTZ  NOT NULL
);

CREATE TABLE vehicle_bookings (
    id            UUID PRIMARY KEY,
    vehicle_id    UUID         NOT NULL REFERENCES vehicles (id) ON DELETE CASCADE,
    user_id       UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    starts_at     TIMESTAMPTZ  NOT NULL,
    ends_at       TIMESTAMPTZ  NOT NULL CHECK (ends_at > starts_at),
    purpose       VARCHAR(200) NOT NULL,
    destination   VARCHAR(120) NOT NULL,
    status        VARCHAR(10)  NOT NULL,
    start_mileage INT,
    end_mileage   INT,
    created_at    TIMESTAMPTZ  NOT NULL
);

CREATE TABLE vehicle_issues (
    id          UUID PRIMARY KEY,
    vehicle_id  UUID          NOT NULL REFERENCES vehicles (id) ON DELETE CASCADE,
    booking_id  UUID REFERENCES vehicle_bookings (id) ON DELETE SET NULL,
    severity    VARCHAR(10)   NOT NULL,
    description VARCHAR(1000) NOT NULL,
    resolved    BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_vehicle_bookings_vehicle ON vehicle_bookings (vehicle_id, starts_at);
