-- 5.19 Feuilles de temps
CREATE TABLE timesheet_reports (
    employee_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    manager_id  UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE timesheet_projects (
    id         UUID PRIMARY KEY,
    code       VARCHAR(20)  NOT NULL UNIQUE,
    name       VARCHAR(120) NOT NULL,
    client     VARCHAR(120),
    status     VARCHAR(10)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL
);

-- projet spécial pour déclarer les absences (congés, maladie) : un jour ouvré déclaré en ABSENCE est rempli
INSERT INTO timesheet_projects (id, code, name, client, status, created_at)
VALUES (gen_random_uuid(), 'ABSENCE', 'Absence (congés, maladie)', NULL, 'OPEN', now());

CREATE TABLE timesheet_weeks (
    id           UUID PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    week         VARCHAR(8)  NOT NULL,
    status       VARCHAR(10) NOT NULL,
    comment      VARCHAR(500),
    submitted_at TIMESTAMPTZ,
    reviewed_by  UUID REFERENCES users (id) ON DELETE SET NULL,
    reviewed_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_timesheet_week UNIQUE (user_id, week)
);

CREATE TABLE timesheet_entries (
    id         UUID PRIMARY KEY,
    week_id    UUID          NOT NULL REFERENCES timesheet_weeks (id) ON DELETE CASCADE,
    project_id UUID          NOT NULL REFERENCES timesheet_projects (id),
    entry_date DATE          NOT NULL,
    hours      NUMERIC(4, 2) NOT NULL CHECK (hours > 0 AND hours <= 10),
    created_at TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_timesheet_entry UNIQUE (week_id, project_id, entry_date)
);
