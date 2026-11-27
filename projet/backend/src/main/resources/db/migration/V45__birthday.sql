-- 5.44 Calendrier des anniversaires
CREATE TABLE birthday_teams (
    id         UUID PRIMARY KEY,
    name       VARCHAR(80) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL
);

-- l'année de naissance est stockée mais jamais renvoyée (sauf à l'intéressé)
CREATE TABLE birthday_profiles (
    user_id       UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    birth_date    DATE,
    hire_date     DATE    NOT NULL,
    team_id       UUID REFERENCES birthday_teams (id) ON DELETE SET NULL,
    show_birthday BOOLEAN NOT NULL DEFAULT TRUE
);
