-- 5.56 Mini-CRM commercial
CREATE TABLE crm_roles (
    user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE crm_accounts (
    id         UUID PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    industry   VARCHAR(80)  NOT NULL,
    size       VARCHAR(10)  NOT NULL,
    owner_id   UUID         NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE TABLE crm_contacts (
    id         UUID PRIMARY KEY,
    account_id UUID         NOT NULL REFERENCES crm_accounts (id) ON DELETE CASCADE,
    first_name VARCHAR(60)  NOT NULL,
    last_name  VARCHAR(60)  NOT NULL,
    email      VARCHAR(254) NOT NULL,
    phone      VARCHAR(30),
    role       VARCHAR(80)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    UNIQUE (account_id, email)
);

CREATE TABLE crm_opportunities (
    id                  UUID PRIMARY KEY,
    account_id          UUID           NOT NULL REFERENCES crm_accounts (id) ON DELETE CASCADE,
    owner_id            UUID           NOT NULL REFERENCES users (id),
    title               VARCHAR(120)   NOT NULL,
    amount              NUMERIC(12, 2) NOT NULL CHECK (amount >= 0),
    expected_close_date DATE           NOT NULL,
    stage               VARCHAR(15)    NOT NULL,
    lost_reason         VARCHAR(500),
    closed_at           TIMESTAMPTZ,
    created_at          TIMESTAMPTZ    NOT NULL
);

CREATE TABLE crm_interactions (
    id             UUID PRIMARY KEY,
    account_id     UUID          NOT NULL REFERENCES crm_accounts (id) ON DELETE CASCADE,
    opportunity_id UUID REFERENCES crm_opportunities (id) ON DELETE CASCADE,
    type           VARCHAR(10)   NOT NULL,
    date           DATE          NOT NULL,
    summary        VARCHAR(2000) NOT NULL,
    author_id      UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at     TIMESTAMPTZ   NOT NULL
);

CREATE TABLE crm_tasks (
    id             UUID PRIMARY KEY,
    owner_id       UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    account_id     UUID REFERENCES crm_accounts (id) ON DELETE CASCADE,
    opportunity_id UUID REFERENCES crm_opportunities (id) ON DELETE CASCADE,
    due_date       DATE         NOT NULL,
    label          VARCHAR(200) NOT NULL,
    done           BOOLEAN      NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL
);
