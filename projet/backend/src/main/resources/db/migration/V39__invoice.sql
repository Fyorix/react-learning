-- 5.38 Facturation & relances
CREATE TABLE invoice_roles (
    user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, role)
);

-- numérotation continue par année, sans trou (attribuée à l'émission)
CREATE TABLE invoice_sequences (
    year       INT PRIMARY KEY,
    last_value INT NOT NULL
);

CREATE TABLE invoice_credit_sequences (
    year       INT PRIMARY KEY,
    last_value INT NOT NULL
);

CREATE TABLE invoices (
    id         UUID PRIMARY KEY,
    number     VARCHAR(20) UNIQUE,
    client_id  UUID           NOT NULL REFERENCES users (id),
    due_date   DATE           NOT NULL,
    issued_at  TIMESTAMPTZ,
    total_ht   NUMERIC(12, 2) NOT NULL,
    total_vat  NUMERIC(12, 2) NOT NULL,
    total_ttc  NUMERIC(12, 2) NOT NULL,
    created_by UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ    NOT NULL
);

CREATE TABLE invoice_lines (
    id         UUID PRIMARY KEY,
    invoice_id UUID           NOT NULL REFERENCES invoices (id) ON DELETE CASCADE,
    position   INT            NOT NULL,
    label      VARCHAR(200)   NOT NULL,
    quantity   NUMERIC(10, 2) NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    vat_rate   NUMERIC(5, 2)  NOT NULL,
    created_at TIMESTAMPTZ    NOT NULL
);

CREATE TABLE invoice_payments (
    id         UUID PRIMARY KEY,
    invoice_id UUID           NOT NULL REFERENCES invoices (id),
    paid_on    DATE           NOT NULL,
    amount     NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    method     VARCHAR(20)    NOT NULL,
    created_by UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ    NOT NULL
);

CREATE TABLE invoice_credit_notes (
    id         UUID PRIMARY KEY,
    invoice_id UUID           NOT NULL REFERENCES invoices (id),
    number     VARCHAR(20)    NOT NULL UNIQUE,
    amount     NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    reason     VARCHAR(500)   NOT NULL,
    created_by UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ    NOT NULL
);

CREATE TABLE invoice_reminders (
    id         UUID PRIMARY KEY,
    invoice_id UUID        NOT NULL REFERENCES invoices (id),
    level      INT         NOT NULL CHECK (level BETWEEN 1 AND 3),
    created_by UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_invoice_reminder UNIQUE (invoice_id, level)
);
