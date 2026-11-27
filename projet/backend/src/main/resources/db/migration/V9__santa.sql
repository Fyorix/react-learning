CREATE TABLE santa_events (
    id             UUID PRIMARY KEY,
    organizer_id   UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name           VARCHAR(120)  NOT NULL,
    event_date     DATE          NOT NULL,
    budget         NUMERIC(8, 2) NOT NULL CHECK (budget > 0),
    draw_deadline  DATE          NOT NULL,
    drawn_at       TIMESTAMPTZ,
    created_at     TIMESTAMPTZ   NOT NULL
);

CREATE TABLE santa_invitations (
    id        UUID PRIMARY KEY,
    event_id  UUID          NOT NULL REFERENCES santa_events (id) ON DELETE CASCADE,
    user_id   UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status    VARCHAR(20)   NOT NULL,
    wishlist  VARCHAR(2000),
    CONSTRAINT uq_santa_invitation UNIQUE (event_id, user_id)
);

CREATE TABLE santa_exclusions (
    id          UUID PRIMARY KEY,
    event_id    UUID NOT NULL REFERENCES santa_events (id) ON DELETE CASCADE,
    giver_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    receiver_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_santa_exclusion UNIQUE (event_id, giver_id, receiver_id)
);

-- attributions du tirage : aucun endpoint ne les expose dans leur ensemble
CREATE TABLE santa_assignments (
    id          UUID PRIMARY KEY,
    event_id    UUID        NOT NULL REFERENCES santa_events (id) ON DELETE CASCADE,
    giver_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    receiver_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    ready       BOOLEAN     NOT NULL DEFAULT FALSE,
    ready_at    TIMESTAMPTZ,
    CONSTRAINT uq_santa_giver UNIQUE (event_id, giver_id),
    CONSTRAINT uq_santa_receiver UNIQUE (event_id, receiver_id),
    CONSTRAINT santa_not_self CHECK (giver_id <> receiver_id)
);
