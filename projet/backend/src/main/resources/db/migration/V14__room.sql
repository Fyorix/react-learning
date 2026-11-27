-- 5.13 Réservation de salles
CREATE TABLE rooms (
    id         UUID PRIMARY KEY,
    name       VARCHAR(80) NOT NULL UNIQUE,
    floor      VARCHAR(30) NOT NULL,
    capacity   INT         NOT NULL CHECK (capacity >= 1),
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE room_equipment (
    room_id   UUID        NOT NULL REFERENCES rooms (id) ON DELETE CASCADE,
    equipment VARCHAR(20) NOT NULL,
    PRIMARY KEY (room_id, equipment)
);

CREATE TABLE room_bookings (
    id         UUID PRIMARY KEY,
    room_id    UUID         NOT NULL REFERENCES rooms (id) ON DELETE CASCADE,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title      VARCHAR(120) NOT NULL,
    attendees  INT          NOT NULL,
    starts_at  TIMESTAMPTZ  NOT NULL,
    ends_at    TIMESTAMPTZ  NOT NULL CHECK (ends_at > starts_at),
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_room_bookings_room_start ON room_bookings (room_id, starts_at);
CREATE INDEX idx_room_bookings_user ON room_bookings (user_id);
