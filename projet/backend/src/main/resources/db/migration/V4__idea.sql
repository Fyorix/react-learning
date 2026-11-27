CREATE TABLE ideas (
    id             UUID PRIMARY KEY,
    author_id      UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title          VARCHAR(120)  NOT NULL,
    description    VARCHAR(2000) NOT NULL,
    category       VARCHAR(30)   NOT NULL,
    status         VARCHAR(20)   NOT NULL,
    status_comment VARCHAR(500),
    vote_count     INT           NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ   NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL
);

CREATE TABLE idea_votes (
    idea_id  UUID        NOT NULL REFERENCES ideas (id) ON DELETE CASCADE,
    user_id  UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    voted_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (idea_id, user_id)
);

CREATE TABLE idea_comments (
    id         UUID PRIMARY KEY,
    idea_id    UUID          NOT NULL REFERENCES ideas (id) ON DELETE CASCADE,
    author_id  UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    content    VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_idea_comments_idea ON idea_comments (idea_id);
