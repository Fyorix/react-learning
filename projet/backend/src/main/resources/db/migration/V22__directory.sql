-- 5.21 Trombinoscope & annuaire
CREATE TABLE directory_teams (
    id         UUID PRIMARY KEY,
    name       VARCHAR(80) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE directory_skills (
    id         UUID PRIMARY KEY,
    name       VARCHAR(60) NOT NULL UNIQUE,
    category   VARCHAR(60),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE directory_profiles (
    user_id    UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    job_title  VARCHAR(100) NOT NULL DEFAULT '',
    team_id    UUID REFERENCES directory_teams (id) ON DELETE SET NULL,
    manager_id UUID REFERENCES users (id) ON DELETE SET NULL,
    location   VARCHAR(80) NOT NULL DEFAULT '',
    phone      VARCHAR(30),
    bio        VARCHAR(1000),
    photo_key  VARCHAR(40),
    photo_type VARCHAR(40)
);

CREATE TABLE directory_profile_skills (
    user_id  UUID NOT NULL REFERENCES directory_profiles (user_id) ON DELETE CASCADE,
    skill_id UUID NOT NULL REFERENCES directory_skills (id) ON DELETE CASCADE,
    level    INT  NOT NULL CHECK (level BETWEEN 1 AND 4),
    PRIMARY KEY (user_id, skill_id)
);
