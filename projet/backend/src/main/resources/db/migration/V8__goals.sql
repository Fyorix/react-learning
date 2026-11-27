-- relation manager → salariés, gérée par un ADMIN (un salarié a au plus un manager)
CREATE TABLE goal_reports (
    employee_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    manager_id  UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT goal_reports_not_self CHECK (employee_id <> manager_id)
);

CREATE TABLE goals (
    id             UUID PRIMARY KEY,
    employee_id    UUID           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    manager_id     UUID           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title          VARCHAR(120)   NOT NULL,
    description    VARCHAR(2000)  NOT NULL,
    metric         VARCHAR(100)   NOT NULL,
    unit           VARCHAR(30)    NOT NULL,
    start_value    NUMERIC(14, 2) NOT NULL,
    target_value   NUMERIC(14, 2) NOT NULL,
    current_value  NUMERIC(14, 2),
    due_date       DATE           NOT NULL,
    status         VARCHAR(20)    NOT NULL,
    review_comment VARCHAR(1000),
    submitted_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ    NOT NULL,
    updated_at     TIMESTAMPTZ    NOT NULL
);

CREATE TABLE goal_progress (
    id         UUID PRIMARY KEY,
    goal_id    UUID           NOT NULL REFERENCES goals (id) ON DELETE CASCADE,
    value      NUMERIC(14, 2) NOT NULL,
    comment    VARCHAR(500),
    created_at TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_goals_employee ON goals (employee_id);
