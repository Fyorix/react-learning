-- 5.15 Notes de frais
-- relation manager → salarié (convention <package>_reports) : les notes elles-mêmes sont dans expense_claims
CREATE TABLE expense_reports (
    employee_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    manager_id  UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE expense_claims (
    id            UUID PRIMARY KEY,
    user_id       UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    month         VARCHAR(7)  NOT NULL,
    status        VARCHAR(12) NOT NULL,
    submitted_at  TIMESTAMPTZ,
    reviewed_by   UUID REFERENCES users (id) ON DELETE SET NULL,
    reviewed_at   TIMESTAMPTZ,
    reimbursed_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_expense_claim_month UNIQUE (user_id, month)
);

CREATE TABLE expense_items (
    id            UUID PRIMARY KEY,
    claim_id      UUID           NOT NULL REFERENCES expense_claims (id) ON DELETE CASCADE,
    expense_date  DATE           NOT NULL,
    category      VARCHAR(12)    NOT NULL,
    amount        NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    description   VARCHAR(300)   NOT NULL,
    justification VARCHAR(500),
    receipt_key   VARCHAR(40)    NOT NULL,
    receipt_type  VARCHAR(60)    NOT NULL,
    receipt_name  VARCHAR(200),
    accepted      BOOLEAN,
    reject_reason VARCHAR(500),
    created_at    TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_expense_items_claim ON expense_items (claim_id);
