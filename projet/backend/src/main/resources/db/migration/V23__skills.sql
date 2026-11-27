-- 5.22 Bourse aux compétences
CREATE TABLE skills_catalog (
    id         UUID PRIMARY KEY,
    name       VARCHAR(60) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE skills_profiles (
    user_id  UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    capacity INT  NOT NULL CHECK (capacity BETWEEN 1 AND 5)
);

CREATE TABLE skills_offers (
    user_id  UUID NOT NULL REFERENCES skills_profiles (user_id) ON DELETE CASCADE,
    skill_id UUID NOT NULL REFERENCES skills_catalog (id) ON DELETE CASCADE,
    level    INT  NOT NULL CHECK (level BETWEEN 1 AND 4),
    PRIMARY KEY (user_id, skill_id)
);

CREATE TABLE skills_wants (
    user_id  UUID NOT NULL REFERENCES skills_profiles (user_id) ON DELETE CASCADE,
    skill_id UUID NOT NULL REFERENCES skills_catalog (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, skill_id)
);

CREATE TABLE skills_requests (
    id         UUID PRIMARY KEY,
    learner_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    mentor_id  UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    skill_id   UUID        NOT NULL REFERENCES skills_catalog (id) ON DELETE CASCADE,
    message    VARCHAR(500),
    status     VARCHAR(10) NOT NULL,
    decided_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE skills_pairs (
    id         UUID PRIMARY KEY,
    mentor_id  UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    learner_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    skill_id   UUID        NOT NULL REFERENCES skills_catalog (id) ON DELETE CASCADE,
    status     VARCHAR(10) NOT NULL,
    ended_at   TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE skills_sessions (
    id               UUID PRIMARY KEY,
    pair_id          UUID         NOT NULL REFERENCES skills_pairs (id) ON DELETE CASCADE,
    author_id        UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    session_date     DATE         NOT NULL,
    duration_minutes INT          NOT NULL CHECK (duration_minutes BETWEEN 15 AND 480),
    notes            VARCHAR(2000),
    created_at       TIMESTAMPTZ  NOT NULL
);
