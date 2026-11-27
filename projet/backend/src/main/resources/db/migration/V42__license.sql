-- 5.41 Licences logicielles
CREATE TABLE license_reports (
    employee_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    manager_id  UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE license_software (
    id             UUID PRIMARY KEY,
    name           VARCHAR(100)   NOT NULL UNIQUE,
    vendor         VARCHAR(100)   NOT NULL,
    seats          INT            NOT NULL CHECK (seats >= 0),
    cost_per_seat  NUMERIC(10, 2) NOT NULL,
    billing        VARCHAR(10)    NOT NULL,
    renewal_date   DATE           NOT NULL,
    status         VARCHAR(20)    NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL
);

-- un siège par logiciel et par salarié
CREATE TABLE license_seats (
    id           UUID PRIMARY KEY,
    software_id  UUID        NOT NULL REFERENCES license_software (id) ON DELETE CASCADE,
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    last_used_on DATE,
    created_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_license_seat UNIQUE (software_id, user_id)
);

CREATE TABLE license_requests (
    id            UUID PRIMARY KEY,
    software_id   UUID         NOT NULL REFERENCES license_software (id) ON DELETE CASCADE,
    user_id       UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    reason        VARCHAR(500) NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    decided_by    UUID REFERENCES users (id) ON DELETE SET NULL,
    reject_reason VARCHAR(300),
    created_at    TIMESTAMPTZ  NOT NULL
);
