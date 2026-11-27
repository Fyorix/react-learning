-- 5.33 Recrutement (ATS)
CREATE TABLE recruitment_roles (
    user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE recruitment_jobs (
    id          UUID PRIMARY KEY,
    title       VARCHAR(120)  NOT NULL,
    team        VARCHAR(80)   NOT NULL,
    description VARCHAR(4000) NOT NULL,
    location    VARCHAR(80)   NOT NULL,
    status      VARCHAR(20)   NOT NULL,
    created_by  UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ   NOT NULL
);

-- une candidature par e-mail et par poste
CREATE TABLE recruitment_applications (
    id              UUID PRIMARY KEY,
    job_id          UUID         NOT NULL REFERENCES recruitment_jobs (id) ON DELETE CASCADE,
    first_name      VARCHAR(60)  NOT NULL,
    last_name       VARCHAR(60)  NOT NULL,
    email           VARCHAR(160) NOT NULL,
    phone           VARCHAR(30),
    cv_key          VARCHAR(200) NOT NULL,
    cv_name         VARCHAR(200),
    stage           VARCHAR(20)  NOT NULL,
    stage_since     TIMESTAMPTZ  NOT NULL,
    hired_at        TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_recruitment_application UNIQUE (job_id, email)
);

CREATE TABLE recruitment_interviewers (
    application_id UUID NOT NULL REFERENCES recruitment_applications (id) ON DELETE CASCADE,
    user_id        UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (application_id, user_id)
);

CREATE TABLE recruitment_stage_events (
    id             UUID PRIMARY KEY,
    application_id UUID        NOT NULL REFERENCES recruitment_applications (id) ON DELETE CASCADE,
    from_stage     VARCHAR(20),
    to_stage       VARCHAR(20) NOT NULL,
    reason         VARCHAR(500),
    moved_by       UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at     TIMESTAMPTZ NOT NULL
);

CREATE TABLE recruitment_evaluations (
    id             UUID PRIMARY KEY,
    application_id UUID          NOT NULL REFERENCES recruitment_applications (id) ON DELETE CASCADE,
    evaluator_id   UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    stage          VARCHAR(20)   NOT NULL,
    rating         INT           NOT NULL CHECK (rating BETWEEN 1 AND 4),
    recommendation VARCHAR(5)    NOT NULL,
    comment        VARCHAR(2000) NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_recruitment_evaluation UNIQUE (application_id, evaluator_id, stage)
);
