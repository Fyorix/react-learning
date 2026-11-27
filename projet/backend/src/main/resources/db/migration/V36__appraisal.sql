-- 5.35 Entretiens annuels
CREATE TABLE appraisal_reports (
    employee_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    manager_id  UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE appraisal_campaigns (
    id         UUID PRIMARY KEY,
    year       INT         NOT NULL UNIQUE,
    deadline   DATE        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE appraisal_questions (
    id          UUID PRIMARY KEY,
    campaign_id UUID         NOT NULL REFERENCES appraisal_campaigns (id) ON DELETE CASCADE,
    position    INT          NOT NULL,
    question    VARCHAR(300) NOT NULL,
    type        VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL
);

-- manager figé au lancement de la campagne
CREATE TABLE appraisals (
    id                   UUID PRIMARY KEY,
    campaign_id          UUID          NOT NULL REFERENCES appraisal_campaigns (id) ON DELETE CASCADE,
    employee_id          UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    manager_id           UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    step                 VARCHAR(30)   NOT NULL,
    final_comment        VARCHAR(2000),
    reopened_until       TIMESTAMPTZ,
    self_submitted_at    TIMESTAMPTZ,
    manager_submitted_at TIMESTAMPTZ,
    employee_signed_at   TIMESTAMPTZ,
    manager_signed_at    TIMESTAMPTZ,
    created_at           TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_appraisal UNIQUE (campaign_id, employee_id)
);

CREATE TABLE appraisal_answers (
    id           UUID PRIMARY KEY,
    appraisal_id UUID          NOT NULL REFERENCES appraisals (id) ON DELETE CASCADE,
    question_id  UUID          NOT NULL REFERENCES appraisal_questions (id) ON DELETE CASCADE,
    author       VARCHAR(10)   NOT NULL,
    text_value   VARCHAR(4000),
    rating_value INT CHECK (rating_value BETWEEN 1 AND 5),
    created_at   TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_appraisal_answer UNIQUE (appraisal_id, question_id, author)
);

CREATE TABLE appraisal_objectives (
    id           UUID PRIMARY KEY,
    appraisal_id UUID          NOT NULL REFERENCES appraisals (id) ON DELETE CASCADE,
    position     INT           NOT NULL,
    title        VARCHAR(200)  NOT NULL,
    description  VARCHAR(1000),
    created_at   TIMESTAMPTZ   NOT NULL
);
