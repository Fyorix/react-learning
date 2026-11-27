-- 5.20 Onboarding des nouveaux arrivants
CREATE TABLE onboarding_templates (
    id         UUID PRIMARY KEY,
    name       VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE TABLE onboarding_template_tasks (
    id              UUID PRIMARY KEY,
    template_id     UUID         NOT NULL REFERENCES onboarding_templates (id) ON DELETE CASCADE,
    title           VARCHAR(150) NOT NULL,
    description     VARCHAR(1000),
    owner           VARCHAR(10)  NOT NULL,
    due_offset_days INT          NOT NULL,
    mandatory       BOOLEAN      NOT NULL,
    position        INT          NOT NULL
);

CREATE TABLE onboarding_journeys (
    id            UUID PRIMARY KEY,
    template_name VARCHAR(100) NOT NULL,
    newcomer_id   UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    manager_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    buddy_id      UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    start_date    DATE         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL
);

-- tâches copiées du modèle au lancement : modifier le modèle ne change pas les parcours lancés
CREATE TABLE onboarding_tasks (
    id          UUID PRIMARY KEY,
    journey_id  UUID         NOT NULL REFERENCES onboarding_journeys (id) ON DELETE CASCADE,
    title       VARCHAR(150) NOT NULL,
    description VARCHAR(1000),
    owner       VARCHAR(10)  NOT NULL,
    due_date    DATE         NOT NULL,
    mandatory   BOOLEAN      NOT NULL,
    position    INT          NOT NULL,
    done_at     TIMESTAMPTZ,
    done_by     UUID REFERENCES users (id) ON DELETE SET NULL
);
