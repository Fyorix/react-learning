-- 5.43 Rétrospective agile
CREATE TABLE retro_teams (
    id         UUID PRIMARY KEY,
    name       VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE retro_team_members (
    team_id UUID NOT NULL REFERENCES retro_teams (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (team_id, user_id)
);

-- timer : timer_ends_at si le décompte tourne, timer_remaining (secondes) s'il est en pause
CREATE TABLE retros (
    id              UUID PRIMARY KEY,
    team_id         UUID         NOT NULL REFERENCES retro_teams (id) ON DELETE CASCADE,
    title           VARCHAR(120) NOT NULL,
    sprint          VARCHAR(40)  NOT NULL,
    facilitator_id  UUID         NOT NULL REFERENCES users (id),
    phase           VARCHAR(20)  NOT NULL,
    timer_ends_at   TIMESTAMPTZ,
    timer_remaining INT,
    created_at      TIMESTAMPTZ  NOT NULL
);

CREATE TABLE retro_groups (
    id         UUID PRIMARY KEY,
    retro_id   UUID         NOT NULL REFERENCES retros (id) ON DELETE CASCADE,
    title      VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE TABLE retro_cards (
    id          UUID PRIMARY KEY,
    retro_id    UUID         NOT NULL REFERENCES retros (id) ON DELETE CASCADE,
    author_id   UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    column_name VARCHAR(20)  NOT NULL,
    text        VARCHAR(500) NOT NULL,
    group_id    UUID REFERENCES retro_groups (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE retro_votes (
    retro_id  UUID NOT NULL REFERENCES retros (id) ON DELETE CASCADE,
    user_id   UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    target_id UUID NOT NULL,
    count     INT  NOT NULL CHECK (count BETWEEN 1 AND 2),
    PRIMARY KEY (retro_id, user_id, target_id)
);

CREATE TABLE retro_actions (
    id         UUID PRIMARY KEY,
    retro_id   UUID         NOT NULL REFERENCES retros (id) ON DELETE CASCADE,
    team_id    UUID         NOT NULL REFERENCES retro_teams (id) ON DELETE CASCADE,
    text       VARCHAR(300) NOT NULL,
    owner_id   UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    due_date   DATE         NOT NULL,
    done       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL
);
