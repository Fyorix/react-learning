-- 5.14 Gestion des congés
CREATE TABLE leave_reports (
    employee_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    manager_id  UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE leave_balances (
    user_id UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    year    INT          NOT NULL,
    paid    NUMERIC(5, 1) NOT NULL CHECK (paid >= 0),
    rtt     NUMERIC(5, 1) NOT NULL CHECK (rtt >= 0),
    PRIMARY KEY (user_id, year)
);

CREATE TABLE leave_requests (
    id          UUID PRIMARY KEY,
    user_id     UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type        VARCHAR(10)   NOT NULL,
    start_date  DATE          NOT NULL,
    end_date    DATE          NOT NULL,
    start_half  VARCHAR(10)   NOT NULL,
    end_half    VARCHAR(10)   NOT NULL,
    days        NUMERIC(5, 1) NOT NULL,
    status      VARCHAR(10)   NOT NULL,
    comment     VARCHAR(500),
    reason      VARCHAR(500),
    decided_by  UUID REFERENCES users (id) ON DELETE SET NULL,
    decided_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_leave_requests_user ON leave_requests (user_id, start_date);
