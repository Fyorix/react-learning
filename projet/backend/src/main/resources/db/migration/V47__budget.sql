-- 5.46 Budget d'équipe
CREATE TABLE budget_teams (
    id         UUID PRIMARY KEY,
    name       VARCHAR(80) NOT NULL UNIQUE,
    manager_id UUID        NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE budget_team_members (
    team_id UUID NOT NULL REFERENCES budget_teams (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (team_id, user_id)
);

CREATE TABLE budget_years (
    year   INT PRIMARY KEY,
    status VARCHAR(10) NOT NULL
);

CREATE TABLE budget_envelopes (
    team_id  UUID           NOT NULL REFERENCES budget_teams (id) ON DELETE CASCADE,
    year     INT            NOT NULL REFERENCES budget_years (year),
    category VARCHAR(20)    NOT NULL,
    amount   NUMERIC(12, 2) NOT NULL CHECK (amount >= 0),
    PRIMARY KEY (team_id, year, category)
);

-- engagé = montant estimé d'une demande APPROVED ; dépensé = montant réel d'une demande SPENT
CREATE TABLE budget_requests (
    id               UUID PRIMARY KEY,
    team_id          UUID           NOT NULL REFERENCES budget_teams (id) ON DELETE CASCADE,
    year             INT            NOT NULL REFERENCES budget_years (year),
    requester_id     UUID           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category         VARCHAR(20)    NOT NULL,
    label            VARCHAR(200)   NOT NULL,
    estimated_amount NUMERIC(12, 2) NOT NULL,
    supplier         VARCHAR(120),
    status           VARCHAR(20)    NOT NULL,
    spent_amount     NUMERIC(12, 2),
    spent_on         DATE,
    decided_by       UUID REFERENCES users (id) ON DELETE SET NULL,
    reason           VARCHAR(500),
    override_reason  VARCHAR(500),
    created_at       TIMESTAMPTZ    NOT NULL
);
