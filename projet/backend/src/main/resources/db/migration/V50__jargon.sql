-- 5.49 Traducteur de jargon
CREATE TABLE jargon_roles (
    user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE jargon_terms (
    id         UUID PRIMARY KEY,
    term       VARCHAR(60)  NOT NULL,
    normalized VARCHAR(60)  NOT NULL,
    expansion  VARCHAR(200),
    team       VARCHAR(60),
    status     VARCHAR(10)  NOT NULL,
    author_id  UUID REFERENCES users (id) ON DELETE SET NULL,
    reason     VARCHAR(500),
    created_at TIMESTAMPTZ  NOT NULL
);

-- unicité insensible à la casse et aux accents (hors propositions refusées)
CREATE UNIQUE INDEX uq_jargon_term ON jargon_terms (normalized) WHERE status <> 'REJECTED';

CREATE TABLE jargon_term_links (
    term_id    UUID NOT NULL REFERENCES jargon_terms (id) ON DELETE CASCADE,
    related_id UUID NOT NULL REFERENCES jargon_terms (id) ON DELETE CASCADE,
    PRIMARY KEY (term_id, related_id)
);

CREATE TABLE jargon_definitions (
    id         UUID PRIMARY KEY,
    term_id    UUID          NOT NULL REFERENCES jargon_terms (id) ON DELETE CASCADE,
    text       VARCHAR(2000) NOT NULL,
    example    VARCHAR(500),
    status     VARCHAR(10)   NOT NULL,
    author_id  UUID REFERENCES users (id) ON DELETE SET NULL,
    reason     VARCHAR(500),
    created_at TIMESTAMPTZ   NOT NULL
);

CREATE TABLE jargon_votes (
    definition_id UUID NOT NULL REFERENCES jargon_definitions (id) ON DELETE CASCADE,
    user_id       UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (definition_id, user_id)
);

-- recherches sans résultat (agrégées, sans lien avec l'utilisateur)
CREATE TABLE jargon_missing (
    normalized VARCHAR(60) PRIMARY KEY,
    query      VARCHAR(60) NOT NULL,
    hits       INT         NOT NULL,
    last_at    TIMESTAMPTZ NOT NULL
);
