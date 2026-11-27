CREATE TABLE polls (
    id                          UUID PRIMARY KEY,
    creator_id                  UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    question                    VARCHAR(200) NOT NULL,
    multiple_choice             BOOLEAN      NOT NULL,
    results_visible_before_vote BOOLEAN      NOT NULL,
    closes_at                   TIMESTAMPTZ,
    closed_at                   TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE poll_options (
    id       UUID PRIMARY KEY,
    poll_id  UUID         NOT NULL REFERENCES polls (id) ON DELETE CASCADE,
    label    VARCHAR(100) NOT NULL,
    position INT          NOT NULL
);

CREATE TABLE poll_votes (
    id        UUID PRIMARY KEY,
    poll_id   UUID        NOT NULL REFERENCES polls (id) ON DELETE CASCADE,
    option_id UUID        NOT NULL REFERENCES poll_options (id) ON DELETE CASCADE,
    user_id   UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    voted_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_poll_vote UNIQUE (poll_id, user_id, option_id)
);

CREATE INDEX idx_poll_votes_poll ON poll_votes (poll_id);
