-- 5.53 Absences étudiantes
CREATE TABLE attendance_roles (
    user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE attendance_groups (
    id         UUID PRIMARY KEY,
    name       VARCHAR(60) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE attendance_group_students (
    group_id   UUID NOT NULL REFERENCES attendance_groups (id) ON DELETE CASCADE,
    student_id UUID NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (group_id, student_id)
);

CREATE TABLE attendance_sessions (
    id         UUID PRIMARY KEY,
    subject    VARCHAR(80) NOT NULL,
    group_id   UUID        NOT NULL REFERENCES attendance_groups (id) ON DELETE CASCADE,
    teacher_id UUID        NOT NULL REFERENCES users (id),
    start_at   TIMESTAMPTZ NOT NULL,
    end_at     TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CHECK (end_at > start_at)
);

CREATE TABLE attendance_entries (
    id         UUID PRIMARY KEY,
    session_id UUID        NOT NULL REFERENCES attendance_sessions (id) ON DELETE CASCADE,
    student_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status     VARCHAR(10) NOT NULL,
    UNIQUE (session_id, student_id)
);

CREATE TABLE attendance_justifications (
    id           UUID PRIMARY KEY,
    student_id   UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    reason       VARCHAR(1000) NOT NULL,
    status       VARCHAR(10)   NOT NULL,
    file_key     VARCHAR(200)  NOT NULL,
    content_type VARCHAR(100)  NOT NULL,
    file_name    VARCHAR(255),
    admin_reason VARCHAR(500),
    decided_by   UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ   NOT NULL
);

CREATE TABLE attendance_justification_entries (
    justification_id UUID NOT NULL REFERENCES attendance_justifications (id) ON DELETE CASCADE,
    entry_id         UUID NOT NULL REFERENCES attendance_entries (id) ON DELETE CASCADE,
    PRIMARY KEY (justification_id, entry_id)
);
