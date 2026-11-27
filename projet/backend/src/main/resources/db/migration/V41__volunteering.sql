-- 5.40 Bénévolat & mécénat de compétences
CREATE TABLE volunteering_reports (
    employee_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    manager_id  UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE volunteering_associations (
    id         UUID PRIMARY KEY,
    name       VARCHAR(120) NOT NULL UNIQUE,
    cause      VARCHAR(60)  NOT NULL,
    active     BOOLEAN      NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE TABLE volunteering_missions (
    id             UUID PRIMARY KEY,
    association_id UUID         NOT NULL REFERENCES volunteering_associations (id),
    title          VARCHAR(150) NOT NULL,
    mission_date   DATE         NOT NULL,
    duration_hours INT          NOT NULL CHECK (duration_hours BETWEEN 1 AND 14),
    location       VARCHAR(150) NOT NULL,
    skills         VARCHAR(300) NOT NULL DEFAULT '',
    seats          INT          NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL
);

CREATE TABLE volunteering_registrations (
    id         UUID PRIMARY KEY,
    mission_id UUID          NOT NULL REFERENCES volunteering_missions (id) ON DELETE CASCADE,
    user_id    UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status     VARCHAR(20)   NOT NULL,
    decided_by UUID REFERENCES users (id) ON DELETE SET NULL,
    reason     VARCHAR(300),
    hours      NUMERIC(4, 1),
    comment    VARCHAR(1000),
    created_at TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_volunteering_registration UNIQUE (mission_id, user_id)
);
