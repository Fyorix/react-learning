CREATE TABLE mood_entries (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    entry_date DATE        NOT NULL,
    score      SMALLINT    NOT NULL CHECK (score BETWEEN 1 AND 5),
    comment    VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_mood_user_date UNIQUE (user_id, entry_date)
);

CREATE TABLE mood_entry_tags (
    entry_id UUID        NOT NULL REFERENCES mood_entries (id) ON DELETE CASCADE,
    tag      VARCHAR(30) NOT NULL,
    PRIMARY KEY (entry_id, tag)
);

CREATE INDEX idx_mood_entries_date ON mood_entries (entry_date);
