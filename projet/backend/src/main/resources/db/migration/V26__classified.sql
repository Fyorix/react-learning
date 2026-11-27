-- 5.25 Annonces internes
CREATE TABLE classifieds (
    id          UUID PRIMARY KEY,
    author_id   UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type        VARCHAR(10)   NOT NULL,
    title       VARCHAR(120)  NOT NULL,
    description VARCHAR(3000) NOT NULL,
    category    VARCHAR(40)   NOT NULL,
    price       NUMERIC(10, 2),
    status      VARCHAR(10)   NOT NULL,
    hidden      BOOLEAN       NOT NULL DEFAULT FALSE,
    expires_at  TIMESTAMPTZ   NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL
);

CREATE TABLE classified_photos (
    id           UUID PRIMARY KEY,
    listing_id   UUID        NOT NULL REFERENCES classifieds (id) ON DELETE CASCADE,
    position     INT         NOT NULL,
    file_key     VARCHAR(40) NOT NULL,
    content_type VARCHAR(40) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL
);

CREATE TABLE classified_messages (
    id           UUID PRIMARY KEY,
    listing_id   UUID          NOT NULL REFERENCES classifieds (id) ON DELETE CASCADE,
    sender_id    UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    recipient_id UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    content      VARCHAR(2000) NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL
);

CREATE TABLE classified_reports (
    listing_id  UUID         NOT NULL REFERENCES classifieds (id) ON DELETE CASCADE,
    reporter_id UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    reason      VARCHAR(500) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (listing_id, reporter_id)
);

CREATE INDEX idx_classifieds_status ON classifieds (status, expires_at);
CREATE INDEX idx_classified_messages_listing ON classified_messages (listing_id, created_at);
