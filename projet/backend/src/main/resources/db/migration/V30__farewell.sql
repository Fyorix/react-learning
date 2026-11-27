-- 5.29 Cagnotte de départ
CREATE TABLE farewell_pots (
    id             UUID PRIMARY KEY,
    organizer_id   UUID           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    beneficiary_id UUID           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    occasion       VARCHAR(20)    NOT NULL,
    title          VARCHAR(120)   NOT NULL,
    goal           NUMERIC(10, 2),
    closes_at      TIMESTAMPTZ,
    invitees_set   BOOLEAN        NOT NULL DEFAULT FALSE,
    status         VARCHAR(20)    NOT NULL,
    closed_at      TIMESTAMPTZ,
    created_at     TIMESTAMPTZ    NOT NULL
);

CREATE TABLE farewell_invitees (
    pot_id  UUID NOT NULL REFERENCES farewell_pots (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (pot_id, user_id)
);

CREATE TABLE farewell_contributions (
    id         UUID PRIMARY KEY,
    pot_id     UUID           NOT NULL REFERENCES farewell_pots (id) ON DELETE CASCADE,
    user_id    UUID           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    amount     NUMERIC(10, 2) NOT NULL,
    message    VARCHAR(500),
    updated_at TIMESTAMPTZ    NOT NULL,
    created_at TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_farewell_contribution UNIQUE (pot_id, user_id)
);
