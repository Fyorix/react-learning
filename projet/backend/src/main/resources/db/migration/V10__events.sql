CREATE TABLE events (
    id                    UUID PRIMARY KEY,
    organizer_id          UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title                 VARCHAR(150)  NOT NULL,
    description           VARCHAR(2000) NOT NULL,
    location              VARCHAR(200)  NOT NULL,
    starts_at             TIMESTAMPTZ   NOT NULL,
    capacity              INT           NOT NULL CHECK (capacity >= 1),
    registration_deadline TIMESTAMPTZ   NOT NULL,
    status                VARCHAR(20)   NOT NULL,
    created_at            TIMESTAMPTZ   NOT NULL
);

CREATE TABLE event_registrations (
    id         UUID PRIMARY KEY,
    event_id   UUID        NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status     VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_event_registration UNIQUE (event_id, user_id)
);

CREATE INDEX idx_events_starts_at ON events (starts_at);
