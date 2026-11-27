-- 5.16 Helpdesk
CREATE TABLE helpdesk_roles (
    user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE helpdesk_tickets (
    id           UUID PRIMARY KEY,
    requester_id UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    assignee_id  UUID REFERENCES users (id) ON DELETE SET NULL,
    title        VARCHAR(150)  NOT NULL,
    description  VARCHAR(4000) NOT NULL,
    category     VARCHAR(20)   NOT NULL,
    priority     VARCHAR(10)   NOT NULL,
    status       VARCHAR(12)   NOT NULL,
    due_at       TIMESTAMPTZ   NOT NULL,
    resolved_at  TIMESTAMPTZ,
    closed_at    TIMESTAMPTZ,
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL
);

CREATE TABLE helpdesk_comments (
    id         UUID PRIMARY KEY,
    ticket_id  UUID          NOT NULL REFERENCES helpdesk_tickets (id) ON DELETE CASCADE,
    author_id  UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    content    VARCHAR(4000) NOT NULL,
    internal   BOOLEAN       NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_helpdesk_tickets_requester ON helpdesk_tickets (requester_id);
CREATE INDEX idx_helpdesk_comments_ticket ON helpdesk_comments (ticket_id, created_at);
