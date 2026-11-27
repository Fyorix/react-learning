-- 5.45 Constructeur de formulaires
CREATE TABLE form_forms (
    id                     UUID PRIMARY KEY,
    owner_id               UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title                  VARCHAR(150)  NOT NULL,
    description            VARCHAR(1000),
    anonymous              BOOLEAN       NOT NULL,
    one_response_per_user  BOOLEAN       NOT NULL,
    closes_at              TIMESTAMPTZ,
    status                 VARCHAR(20)   NOT NULL,
    published_version      INT,
    draft_version          INT,
    created_at             TIMESTAMPTZ   NOT NULL
);

-- structure versionnée : liste de champs en JSON
CREATE TABLE form_versions (
    id         UUID PRIMARY KEY,
    form_id    UUID        NOT NULL REFERENCES form_forms (id) ON DELETE CASCADE,
    number     INT         NOT NULL,
    fields     TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_form_version UNIQUE (form_id, number)
);

-- qui a répondu (pour « une réponse par personne », y compris en mode anonyme) : sans lien avec la réponse
CREATE TABLE form_respondents (
    form_id UUID NOT NULL REFERENCES form_forms (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (form_id, user_id)
);

CREATE TABLE form_responses (
    id         UUID PRIMARY KEY,
    form_id    UUID        NOT NULL REFERENCES form_forms (id) ON DELETE CASCADE,
    version    INT         NOT NULL,
    user_id    UUID REFERENCES users (id) ON DELETE SET NULL,
    answers    TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_form_responses_form ON form_responses (form_id, version, created_at DESC);
