-- 5.50 Planificateur de réunions
CREATE TABLE meeting_polls (
    id               UUID PRIMARY KEY,
    title            VARCHAR(120) NOT NULL,
    duration_minutes INT          NOT NULL,
    location         VARCHAR(120),
    organizer_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status           VARCHAR(10)  NOT NULL,
    decided_slot_id  UUID,
    last_reminder_at TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL
);

CREATE TABLE meeting_poll_participants (
    poll_id UUID NOT NULL REFERENCES meeting_polls (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (poll_id, user_id)
);

CREATE TABLE meeting_slots (
    id       UUID PRIMARY KEY,
    poll_id  UUID        NOT NULL REFERENCES meeting_polls (id) ON DELETE CASCADE,
    start_at TIMESTAMPTZ NOT NULL,
    UNIQUE (poll_id, start_at)
);

CREATE TABLE meeting_answers (
    slot_id UUID        NOT NULL REFERENCES meeting_slots (id) ON DELETE CASCADE,
    user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    answer  VARCHAR(10) NOT NULL,
    PRIMARY KEY (slot_id, user_id)
);
