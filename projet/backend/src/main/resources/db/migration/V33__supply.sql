-- 5.32 Fournitures de bureau
CREATE TABLE supply_reports (
    employee_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    manager_id  UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE
);

-- stock physique ; reserved = quantités des demandes validées pas encore remises
CREATE TABLE supply_items (
    id                UUID PRIMARY KEY,
    name              VARCHAR(100) NOT NULL UNIQUE,
    category          VARCHAR(50)  NOT NULL,
    unit              VARCHAR(30)  NOT NULL,
    stock             INT          NOT NULL CHECK (stock >= 0),
    reserved          INT          NOT NULL DEFAULT 0 CHECK (reserved >= 0 AND reserved <= stock),
    reorder_threshold INT          NOT NULL,
    monthly_quota     INT,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ  NOT NULL
);

CREATE TABLE supply_requests (
    id           UUID PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status       VARCHAR(20) NOT NULL,
    reason       VARCHAR(300),
    decided_by   UUID REFERENCES users (id) ON DELETE SET NULL,
    delivered_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL
);

CREATE TABLE supply_lines (
    id         UUID PRIMARY KEY,
    request_id UUID        NOT NULL REFERENCES supply_requests (id) ON DELETE CASCADE,
    item_id    UUID        NOT NULL REFERENCES supply_items (id),
    quantity   INT         NOT NULL CHECK (quantity > 0),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE supply_restocks (
    id         UUID PRIMARY KEY,
    item_id    UUID        NOT NULL REFERENCES supply_items (id) ON DELETE CASCADE,
    quantity   INT         NOT NULL,
    created_by UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_supply_lines_request ON supply_lines (request_id);
