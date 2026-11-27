-- 5.12 Devis & catalogue produits
CREATE TABLE quote_roles (
    user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE quote_products (
    id         UUID PRIMARY KEY,
    reference  VARCHAR(30)    NOT NULL UNIQUE,
    name       VARCHAR(150)   NOT NULL,
    category   VARCHAR(60)    NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    vat_rate   NUMERIC(4, 1)  NOT NULL,
    archived   BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ    NOT NULL
);

CREATE TABLE quotes (
    id              UUID PRIMARY KEY,
    number          VARCHAR(20) UNIQUE,
    sales_id        UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    client_id       UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title           VARCHAR(150) NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    valid_until     DATE,
    sent_at         TIMESTAMPTZ,
    decided_at      TIMESTAMPTZ,
    refusal_comment VARCHAR(1000),
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL
);

-- reference / label / unit_price / vat_rate : copiés à l'envoi (null tant que le devis est en brouillon)
CREATE TABLE quote_lines (
    id         UUID PRIMARY KEY,
    quote_id   UUID          NOT NULL REFERENCES quotes (id) ON DELETE CASCADE,
    product_id UUID          NOT NULL REFERENCES quote_products (id),
    position   INT           NOT NULL,
    quantity   INT           NOT NULL CHECK (quantity >= 1),
    discount   NUMERIC(5, 2) NOT NULL CHECK (discount BETWEEN 0 AND 100),
    reference  VARCHAR(30),
    label      VARCHAR(150),
    unit_price NUMERIC(12, 2),
    vat_rate   NUMERIC(4, 1)
);

CREATE TABLE quote_sequences (
    year       INT PRIMARY KEY,
    last_value INT NOT NULL
);

CREATE INDEX idx_quotes_sales ON quotes (sales_id);
CREATE INDEX idx_quotes_client ON quotes (client_id);
CREATE INDEX idx_quote_lines_quote ON quote_lines (quote_id);
