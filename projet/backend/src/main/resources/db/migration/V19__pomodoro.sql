-- 5.18 Pomodoro partagé
CREATE TABLE pomodoro_rooms (
    id                       UUID PRIMARY KEY,
    name                     VARCHAR(80) NOT NULL,
    host_id                  UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    focus_minutes            INT         NOT NULL,
    short_break_minutes      INT         NOT NULL,
    long_break_minutes       INT         NOT NULL,
    cycles_before_long_break INT         NOT NULL,
    phase                    VARCHAR(12) NOT NULL,
    paused_phase             VARCHAR(12),
    phase_started_at         TIMESTAMPTZ,
    phase_ends_at            TIMESTAMPTZ,
    paused_remaining_seconds BIGINT,
    cycle                    INT         NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ NOT NULL
);

CREATE TABLE pomodoro_members (
    room_id       UUID NOT NULL REFERENCES pomodoro_rooms (id) ON DELETE CASCADE,
    user_id       UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    last_seen_at  TIMESTAMPTZ,
    present_since TIMESTAMPTZ,
    PRIMARY KEY (room_id, user_id)
);

CREATE TABLE pomodoro_completed (
    id           UUID PRIMARY KEY,
    room_id      UUID        NOT NULL REFERENCES pomodoro_rooms (id) ON DELETE CASCADE,
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    completed_at TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_pomodoro_completed_user ON pomodoro_completed (user_id, completed_at);
