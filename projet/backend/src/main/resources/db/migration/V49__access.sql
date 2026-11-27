-- 5.48 Demandes d'accès
CREATE TABLE access_reports (
    employee_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    manager_id  UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE access_tools (
    id         UUID PRIMARY KEY,
    name       VARCHAR(80) NOT NULL UNIQUE,
    owner_id   UUID        NOT NULL REFERENCES users (id),
    levels     VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE access_requests (
    id            UUID PRIMARY KEY,
    tool_id       UUID          NOT NULL REFERENCES access_tools (id) ON DELETE CASCADE,
    user_id       UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    level         VARCHAR(10)   NOT NULL,
    justification VARCHAR(1000) NOT NULL,
    duration_days INT           NOT NULL,
    status        VARCHAR(20)   NOT NULL,
    manager_id    UUID REFERENCES users (id) ON DELETE SET NULL,
    reason        VARCHAR(500),
    decided_by    UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ   NOT NULL
);

CREATE TABLE access_grants (
    id                UUID PRIMARY KEY,
    tool_id           UUID        NOT NULL REFERENCES access_tools (id) ON DELETE CASCADE,
    user_id           UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    level             VARCHAR(10) NOT NULL,
    request_id        UUID REFERENCES access_requests (id) ON DELETE SET NULL,
    expires_at        TIMESTAMPTZ NOT NULL,
    status            VARCHAR(10) NOT NULL,
    last_confirmed_at TIMESTAMPTZ NOT NULL,
    ended_at          TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL
);

-- au plus un accès actif par utilisateur et par outil
CREATE UNIQUE INDEX uq_access_active_grant ON access_grants (tool_id, user_id) WHERE status = 'ACTIVE';

CREATE TABLE access_audit (
    id         UUID PRIMARY KEY,
    action     VARCHAR(20) NOT NULL,
    tool_id    UUID        NOT NULL REFERENCES access_tools (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    level      VARCHAR(10) NOT NULL,
    actor_id   UUID REFERENCES users (id) ON DELETE SET NULL,
    detail     VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL
);
