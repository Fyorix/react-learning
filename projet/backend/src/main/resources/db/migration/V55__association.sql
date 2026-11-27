-- 5.54 Gestion d'association
CREATE TABLE association_roles (
    user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE association_fees (
    id          UUID PRIMARY KEY,
    user_id     UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    season      VARCHAR(9)    NOT NULL,
    category    VARCHAR(10)   NOT NULL,
    amount      NUMERIC(8, 2) NOT NULL CHECK (amount > 0),
    paid_at     DATE          NOT NULL,
    recorded_by UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ   NOT NULL,
    UNIQUE (user_id, season)
);

CREATE TABLE association_activities (
    id           UUID PRIMARY KEY,
    title        VARCHAR(120) NOT NULL,
    date         TIMESTAMPTZ  NOT NULL,
    capacity     INT          NOT NULL CHECK (capacity > 0),
    members_only BOOLEAN      NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL
);

CREATE TABLE association_registrations (
    id          UUID PRIMARY KEY,
    activity_id UUID        NOT NULL REFERENCES association_activities (id) ON DELETE CASCADE,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status      VARCHAR(12) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    UNIQUE (activity_id, user_id)
);

CREATE TABLE association_assemblies (
    id             UUID PRIMARY KEY,
    title          VARCHAR(120) NOT NULL,
    date           TIMESTAMPTZ  NOT NULL,
    quorum_percent INT          NOT NULL CHECK (quorum_percent BETWEEN 1 AND 100),
    status         VARCHAR(10)  NOT NULL,
    eligible       INT,
    voters         INT,
    created_at     TIMESTAMPTZ  NOT NULL
);

-- vote secret : décompte par résolution, émargement séparé sans le choix
CREATE TABLE association_resolutions (
    id            UUID PRIMARY KEY,
    assembly_id   UUID          NOT NULL REFERENCES association_assemblies (id) ON DELETE CASCADE,
    position      INT           NOT NULL,
    title         VARCHAR(120)  NOT NULL,
    text          VARCHAR(2000) NOT NULL,
    for_count     INT           NOT NULL DEFAULT 0,
    against_count INT           NOT NULL DEFAULT 0,
    abstain_count INT           NOT NULL DEFAULT 0
);

CREATE TABLE association_ballots (
    resolution_id UUID NOT NULL REFERENCES association_resolutions (id) ON DELETE CASCADE,
    user_id       UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (resolution_id, user_id)
);
