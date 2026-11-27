-- 5.27 Parking d'entreprise
CREATE TABLE parking_spots (
    id         UUID PRIMARY KEY,
    number     VARCHAR(10) NOT NULL UNIQUE,
    level      VARCHAR(20) NOT NULL,
    holder_id  UUID UNIQUE REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE parking_releases (
    spot_id    UUID        NOT NULL REFERENCES parking_spots (id) ON DELETE CASCADE,
    day        DATE        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (spot_id, day)
);

-- une place par jour, une réservation par personne et par jour : garanti aussi par la base
CREATE TABLE parking_bookings (
    id         UUID PRIMARY KEY,
    spot_id    UUID        NOT NULL REFERENCES parking_spots (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    day        DATE        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_parking_spot_day UNIQUE (spot_id, day),
    CONSTRAINT uq_parking_user_day UNIQUE (user_id, day)
);
