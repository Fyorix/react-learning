-- 5.28 Commande groupée
CREATE TABLE grouporder_suppliers (
    id            UUID PRIMARY KEY,
    name          VARCHAR(80)    NOT NULL UNIQUE,
    minimum_order NUMERIC(10, 2) NOT NULL,
    delivery_fee  NUMERIC(10, 2) NOT NULL,
    created_at    TIMESTAMPTZ    NOT NULL
);

-- un article retiré de la carte est archivé (les commandes passées gardent leur historique)
CREATE TABLE grouporder_items (
    id          UUID PRIMARY KEY,
    supplier_id UUID           NOT NULL REFERENCES grouporder_suppliers (id) ON DELETE CASCADE,
    name        VARCHAR(80)    NOT NULL,
    price       NUMERIC(10, 2) NOT NULL,
    available   BOOLEAN        NOT NULL,
    archived    BOOLEAN        NOT NULL DEFAULT FALSE,
    position    INT            NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL
);

CREATE TABLE grouporder_options (
    id          UUID PRIMARY KEY,
    item_id     UUID           NOT NULL REFERENCES grouporder_items (id) ON DELETE CASCADE,
    label       VARCHAR(60)    NOT NULL,
    extra_price NUMERIC(10, 2) NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL
);

CREATE TABLE grouporder_orders (
    id           UUID PRIMARY KEY,
    supplier_id  UUID         NOT NULL REFERENCES grouporder_suppliers (id),
    organizer_id UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    deadline     TIMESTAMPTZ  NOT NULL,
    note         VARCHAR(500),
    status       VARCHAR(20)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL
);

-- libellé et prix unitaire (article + options) figés à l'ajout
CREATE TABLE grouporder_lines (
    id         UUID PRIMARY KEY,
    order_id   UUID           NOT NULL REFERENCES grouporder_orders (id) ON DELETE CASCADE,
    user_id    UUID           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    item_id    UUID           NOT NULL REFERENCES grouporder_items (id),
    label      VARCHAR(300)   NOT NULL,
    unit_price NUMERIC(10, 2) NOT NULL,
    quantity   INT            NOT NULL,
    created_at TIMESTAMPTZ    NOT NULL
);

CREATE TABLE grouporder_line_options (
    line_id   UUID NOT NULL REFERENCES grouporder_lines (id) ON DELETE CASCADE,
    option_id UUID NOT NULL
);

CREATE TABLE grouporder_payments (
    order_id UUID    NOT NULL REFERENCES grouporder_orders (id) ON DELETE CASCADE,
    user_id  UUID    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    paid     BOOLEAN NOT NULL,
    PRIMARY KEY (order_id, user_id)
);

CREATE INDEX idx_grouporder_lines_order ON grouporder_lines (order_id);
