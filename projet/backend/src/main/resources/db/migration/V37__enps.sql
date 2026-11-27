-- 5.36 Baromètre eNPS anonyme
CREATE TABLE enps_teams (
    id         UUID PRIMARY KEY,
    name       VARCHAR(80) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE enps_team_members (
    user_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    team_id UUID NOT NULL REFERENCES enps_teams (id) ON DELETE CASCADE
);

CREATE TABLE enps_surveys (
    id         UUID PRIMARY KEY,
    title      VARCHAR(150) NOT NULL,
    opens_at   TIMESTAMPTZ  NOT NULL,
    closes_at  TIMESTAMPTZ  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE TABLE enps_questions (
    id         UUID PRIMARY KEY,
    survey_id  UUID         NOT NULL REFERENCES enps_surveys (id) ON DELETE CASCADE,
    position   INT          NOT NULL,
    label      VARCHAR(300) NOT NULL,
    type       VARCHAR(20)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL
);

-- ANONYMAT : qui a répondu (participations) et ce qui a été répondu (responses) n'ont AUCUNE colonne commune
-- hormis l'enquête ; la réponse ne garde que l'équipe et aucune date (pas de recoupement temporel possible).
CREATE TABLE enps_participations (
    survey_id UUID NOT NULL REFERENCES enps_surveys (id) ON DELETE CASCADE,
    user_id   UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (survey_id, user_id)
);

CREATE TABLE enps_responses (
    id        UUID PRIMARY KEY,
    survey_id UUID NOT NULL REFERENCES enps_surveys (id) ON DELETE CASCADE,
    team_id   UUID REFERENCES enps_teams (id) ON DELETE SET NULL
);

CREATE TABLE enps_answers (
    id          UUID PRIMARY KEY,
    response_id UUID NOT NULL REFERENCES enps_responses (id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES enps_questions (id) ON DELETE CASCADE,
    score       INT CHECK (score BETWEEN 0 AND 10),
    text        VARCHAR(2000)
);
