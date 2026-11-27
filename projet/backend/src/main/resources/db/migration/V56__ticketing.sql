-- 5.55 Billetterie
CREATE TABLE ticketing_roles (
    user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE ticketing_events (
    id           UUID PRIMARY KEY,
    title        VARCHAR(120) NOT NULL,
    venue        VARCHAR(120) NOT NULL,
    starts_at    TIMESTAMPTZ  NOT NULL,
    capacity     INT          NOT NULL CHECK (capacity > 0),
    sales_end_at TIMESTAMPTZ  NOT NULL,
    organizer_id UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ  NOT NULL
);

CREATE TABLE ticketing_rates (
    id       UUID PRIMARY KEY,
    event_id UUID          NOT NULL REFERENCES ticketing_events (id) ON DELETE CASCADE,
    position INT           NOT NULL,
    label    VARCHAR(60)   NOT NULL,
    price    NUMERIC(8, 2) NOT NULL CHECK (price >= 0),
    quota    INT           NOT NULL CHECK (quota >= 0)
);

CREATE TABLE ticketing_orders (
    id         UUID PRIMARY KEY,
    event_id   UUID           NOT NULL REFERENCES ticketing_events (id) ON DELETE CASCADE,
    buyer_id   UUID           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status     VARCHAR(10)    NOT NULL,
    total      NUMERIC(10, 2) NOT NULL,
    created_at TIMESTAMPTZ    NOT NULL
);

CREATE TABLE ticketing_tickets (
    id         UUID PRIMARY KEY,
    order_id   UUID        NOT NULL REFERENCES ticketing_orders (id) ON DELETE CASCADE,
    event_id   UUID        NOT NULL REFERENCES ticketing_events (id) ON DELETE CASCADE,
    rate_id    UUID        NOT NULL REFERENCES ticketing_rates (id),
    code       VARCHAR(60) NOT NULL UNIQUE,
    status     VARCHAR(10) NOT NULL,
    scanned_at TIMESTAMPTZ,
    scanned_by UUID REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_ticketing_tickets_event ON ticketing_tickets (event_id, status);
