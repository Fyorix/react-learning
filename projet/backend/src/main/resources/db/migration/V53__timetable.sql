-- 5.52 Emploi du temps
CREATE TABLE timetable_roles (
    user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE timetable_rooms (
    id         UUID PRIMARY KEY,
    name       VARCHAR(40) NOT NULL UNIQUE,
    capacity   INT         NOT NULL CHECK (capacity > 0),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE timetable_groups (
    id         UUID PRIMARY KEY,
    name       VARCHAR(60) NOT NULL UNIQUE,
    size       INT         NOT NULL CHECK (size > 0),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE timetable_group_students (
    group_id   UUID NOT NULL REFERENCES timetable_groups (id) ON DELETE CASCADE,
    student_id UUID NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (group_id, student_id)
);

CREATE TABLE timetable_subjects (
    id            UUID PRIMARY KEY,
    name          VARCHAR(80) NOT NULL,
    planned_hours INT         NOT NULL CHECK (planned_hours > 0),
    group_id      UUID        NOT NULL REFERENCES timetable_groups (id) ON DELETE CASCADE,
    created_at    TIMESTAMPTZ NOT NULL,
    UNIQUE (group_id, name)
);

CREATE TABLE timetable_courses (
    id         UUID PRIMARY KEY,
    subject_id UUID        NOT NULL REFERENCES timetable_subjects (id) ON DELETE CASCADE,
    group_id   UUID        NOT NULL REFERENCES timetable_groups (id) ON DELETE CASCADE,
    teacher_id UUID        NOT NULL REFERENCES users (id),
    room_id    UUID        NOT NULL REFERENCES timetable_rooms (id),
    start_at   TIMESTAMPTZ NOT NULL,
    end_at     TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CHECK (end_at > start_at)
);

CREATE INDEX idx_timetable_courses_start ON timetable_courses (start_at);
