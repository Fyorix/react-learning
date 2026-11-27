-- 5.51 Notes & bulletins
CREATE TABLE grade_roles (
    user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE grade_classes (
    id         UUID PRIMARY KEY,
    name       VARCHAR(60) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL
);

-- un étudiant appartient à une seule classe
CREATE TABLE grade_class_students (
    class_id   UUID NOT NULL REFERENCES grade_classes (id) ON DELETE CASCADE,
    student_id UUID NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (class_id, student_id)
);

CREATE TABLE grade_subjects (
    id          UUID PRIMARY KEY,
    name        VARCHAR(80)   NOT NULL,
    coefficient NUMERIC(4, 2) NOT NULL CHECK (coefficient > 0),
    teacher_id  UUID          NOT NULL REFERENCES users (id),
    class_id    UUID          NOT NULL REFERENCES grade_classes (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ   NOT NULL,
    UNIQUE (class_id, name)
);

CREATE TABLE grade_periods (
    id         UUID PRIMARY KEY,
    name       VARCHAR(60) NOT NULL UNIQUE,
    start_date DATE        NOT NULL,
    end_date   DATE        NOT NULL,
    locked     BOOLEAN     NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CHECK (end_date >= start_date)
);

CREATE TABLE grade_assessments (
    id         UUID PRIMARY KEY,
    subject_id UUID          NOT NULL REFERENCES grade_subjects (id) ON DELETE CASCADE,
    period_id  UUID          NOT NULL REFERENCES grade_periods (id) ON DELETE CASCADE,
    title      VARCHAR(120)  NOT NULL,
    date       DATE          NOT NULL,
    weight     NUMERIC(3, 1) NOT NULL CHECK (weight BETWEEN 0.5 AND 3),
    created_at TIMESTAMPTZ   NOT NULL
);

CREATE TABLE grade_marks (
    assessment_id UUID          NOT NULL REFERENCES grade_assessments (id) ON DELETE CASCADE,
    student_id    UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    value         NUMERIC(4, 2) CHECK (value BETWEEN 0 AND 20),
    status        VARCHAR(20)   NOT NULL,
    PRIMARY KEY (assessment_id, student_id)
);

CREATE TABLE grade_comments (
    period_id  UUID          NOT NULL REFERENCES grade_periods (id) ON DELETE CASCADE,
    student_id UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- NULL : appréciation générale (ADMIN)
    subject_id UUID REFERENCES grade_subjects (id) ON DELETE CASCADE,
    id         UUID PRIMARY KEY,
    text       VARCHAR(1000) NOT NULL
);

CREATE UNIQUE INDEX uq_grade_comment ON grade_comments (period_id, student_id, COALESCE(subject_id, '00000000-0000-0000-0000-000000000000'));
