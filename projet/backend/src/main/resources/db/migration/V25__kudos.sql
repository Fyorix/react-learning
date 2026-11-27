-- 5.24 Mur de kudos
CREATE TABLE kudos_values (
    id         UUID PRIMARY KEY,
    name       VARCHAR(60) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE kudos (
    id         UUID PRIMARY KEY,
    author_id  UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    message    VARCHAR(500) NOT NULL,
    value_id   UUID         NOT NULL REFERENCES kudos_values (id),
    moderated  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE TABLE kudos_recipients (
    kudo_id UUID NOT NULL REFERENCES kudos (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (kudo_id, user_id)
);

CREATE TABLE kudos_reactions (
    kudo_id UUID       NOT NULL REFERENCES kudos (id) ON DELETE CASCADE,
    user_id UUID       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    emoji   VARCHAR(8) NOT NULL,
    PRIMARY KEY (kudo_id, user_id, emoji)
);

-- pagination par curseur : (created_at, id) décroissants
CREATE INDEX idx_kudos_wall ON kudos (created_at DESC, id DESC);
