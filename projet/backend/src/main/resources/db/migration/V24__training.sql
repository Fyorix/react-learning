-- 5.23 Formations internes
CREATE TABLE training_reports (
    employee_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    manager_id  UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE training_courses (
    id             UUID PRIMARY KEY,
    title          VARCHAR(150)  NOT NULL,
    theme          VARCHAR(60)   NOT NULL,
    trainer_id     UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    duration_hours NUMERIC(4, 1) NOT NULL,
    description    VARCHAR(2000) NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL
);

CREATE TABLE training_sessions (
    id         UUID PRIMARY KEY,
    course_id  UUID         NOT NULL REFERENCES training_courses (id) ON DELETE CASCADE,
    starts_at  TIMESTAMPTZ  NOT NULL,
    ends_at    TIMESTAMPTZ  NOT NULL CHECK (ends_at > starts_at),
    location   VARCHAR(120) NOT NULL,
    seats      INT          NOT NULL CHECK (seats >= 1),
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE TABLE training_enrollments (
    id          UUID PRIMARY KEY,
    session_id  UUID        NOT NULL REFERENCES training_sessions (id) ON DELETE CASCADE,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status      VARCHAR(12) NOT NULL,
    reason      VARCHAR(500),
    approved_at TIMESTAMPTZ,
    present     BOOLEAN,
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_training_enrollments_session ON training_enrollments (session_id);
CREATE INDEX idx_training_enrollments_user ON training_enrollments (user_id);
