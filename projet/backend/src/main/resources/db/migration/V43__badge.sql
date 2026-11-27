-- 5.42 Badges & gamification
CREATE TABLE badge_badges (
    id          UUID PRIMARY KEY,
    code        VARCHAR(40)  NOT NULL UNIQUE,
    name        VARCHAR(80)  NOT NULL,
    icon        VARCHAR(16)  NOT NULL,
    description VARCHAR(300) NOT NULL,
    rarity      VARCHAR(20)  NOT NULL,
    secret      BOOLEAN      NOT NULL,
    rule_type   VARCHAR(20)  NOT NULL,
    event       VARCHAR(40),
    threshold   INT,
    tier_bronze INT,
    tier_silver INT,
    tier_gold   INT,
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE badge_events (
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type        VARCHAR(40) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL
);

-- un badge (ou palier) une seule fois, jamais retiré ; tier = NONE pour un badge sans palier
CREATE TABLE badge_awards (
    id         UUID PRIMARY KEY,
    badge_id   UUID         NOT NULL REFERENCES badge_badges (id) ON DELETE CASCADE,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    tier       VARCHAR(10)  NOT NULL,
    points     INT          NOT NULL,
    reason     VARCHAR(300),
    awarded_by UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_badge_award UNIQUE (badge_id, user_id, tier)
);

CREATE INDEX idx_badge_events_user_type ON badge_events (user_id, type);
CREATE INDEX idx_badge_awards_created ON badge_awards (created_at DESC);
