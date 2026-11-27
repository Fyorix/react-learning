CREATE TABLE inventory_equipment (
    id            UUID PRIMARY KEY,
    type          VARCHAR(20)  NOT NULL,
    brand         VARCHAR(60)  NOT NULL,
    model         VARCHAR(100) NOT NULL,
    serial_number VARCHAR(100) NOT NULL UNIQUE,
    purchased_at  DATE         NOT NULL,
    status        VARCHAR(20)  NOT NULL CHECK (status IN ('DISPONIBLE', 'ATTRIBUE', 'EN_PANNE', 'REBUT')),
    holder_id     UUID REFERENCES users (id),
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT inventory_holder_iff_assigned CHECK ((status = 'ATTRIBUE') = (holder_id IS NOT NULL))
);

CREATE TABLE inventory_assignments (
    id           UUID PRIMARY KEY,
    equipment_id UUID        NOT NULL REFERENCES inventory_equipment (id) ON DELETE CASCADE,
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    assigned_at  TIMESTAMPTZ NOT NULL,
    released_at  TIMESTAMPTZ
);

CREATE TABLE inventory_issues (
    id           UUID PRIMARY KEY,
    equipment_id UUID          NOT NULL REFERENCES inventory_equipment (id) ON DELETE CASCADE,
    reporter_id  UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    description  VARCHAR(1000) NOT NULL,
    status       VARCHAR(20)   NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL,
    resolved_at  TIMESTAMPTZ
);
