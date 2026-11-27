-- 5.47 Planning des soutenances
CREATE TABLE defense_roles (
    user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE defense_slots (
    id               UUID PRIMARY KEY,
    start_at         TIMESTAMPTZ NOT NULL,
    duration_minutes INT         NOT NULL,
    room             VARCHAR(40) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL
);

CREATE TABLE defense_jury (
    slot_id    UUID NOT NULL REFERENCES defense_slots (id) ON DELETE CASCADE,
    teacher_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (slot_id, teacher_id)
);

-- un créneau par groupe et un groupe par créneau
CREATE TABLE defense_groups (
    id            UUID PRIMARY KEY,
    name          VARCHAR(80) NOT NULL UNIQUE,
    supervisor_id UUID        NOT NULL REFERENCES users (id),
    slot_id       UUID UNIQUE REFERENCES defense_slots (id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ NOT NULL
);

CREATE TABLE defense_group_students (
    group_id   UUID NOT NULL REFERENCES defense_groups (id) ON DELETE CASCADE,
    student_id UUID NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (group_id, student_id)
);

CREATE TABLE defense_grades (
    slot_id    UUID          NOT NULL REFERENCES defense_slots (id) ON DELETE CASCADE,
    teacher_id UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    grade      NUMERIC(4, 2) NOT NULL CHECK (grade BETWEEN 0 AND 20),
    comment    VARCHAR(2000) NOT NULL,
    PRIMARY KEY (slot_id, teacher_id)
);
