-- 5.39 Cours de sport à crédits
CREATE TABLE fitness_roles (
    user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE fitness_classes (
    id               UUID PRIMARY KEY,
    type             VARCHAR(40) NOT NULL,
    coach_id         UUID        NOT NULL REFERENCES users (id),
    starts_at        TIMESTAMPTZ NOT NULL,
    duration_minutes INT         NOT NULL,
    capacity         INT         NOT NULL,
    cancelled        BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ NOT NULL
);

-- debited : le crédit du mois du cours est consommé (réservation confirmée ou annulation tardive)
CREATE TABLE fitness_bookings (
    id         UUID PRIMARY KEY,
    class_id   UUID        NOT NULL REFERENCES fitness_classes (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status     VARCHAR(20) NOT NULL,
    debited    BOOLEAN     NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_fitness_booking UNIQUE (class_id, user_id)
);

CREATE INDEX idx_fitness_classes_start ON fitness_classes (starts_at);
