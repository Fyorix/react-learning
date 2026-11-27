-- 5.31 Planning d'astreintes
CREATE TABLE oncall_teams (
    id         UUID PRIMARY KEY,
    name       VARCHAR(80) NOT NULL UNIQUE,
    manager_id UUID        NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE oncall_members (
    team_id UUID NOT NULL REFERENCES oncall_teams (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (team_id, user_id)
);

-- exactement une personne par équipe et par semaine (lundi)
CREATE TABLE oncall_shifts (
    id         UUID PRIMARY KEY,
    team_id    UUID        NOT NULL REFERENCES oncall_teams (id) ON DELETE CASCADE,
    week_start DATE        NOT NULL,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_oncall_shift UNIQUE (team_id, week_start)
);

CREATE TABLE oncall_unavailabilities (
    id         UUID PRIMARY KEY,
    team_id    UUID         NOT NULL REFERENCES oncall_teams (id) ON DELETE CASCADE,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    week_start DATE         NOT NULL,
    reason     VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_oncall_unavailability UNIQUE (team_id, user_id, week_start)
);

CREATE TABLE oncall_swaps (
    id            UUID PRIMARY KEY,
    team_id       UUID        NOT NULL REFERENCES oncall_teams (id) ON DELETE CASCADE,
    requester_id  UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    my_week       DATE        NOT NULL,
    other_user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    other_week    DATE        NOT NULL,
    status        VARCHAR(20) NOT NULL,
    decided_by    UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ NOT NULL
);
