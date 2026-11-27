-- 5.34 Cooptation
CREATE TABLE referral_jobs (
    id           UUID PRIMARY KEY,
    title        VARCHAR(120)   NOT NULL,
    team         VARCHAR(80)    NOT NULL,
    description  VARCHAR(4000)  NOT NULL,
    bonus_amount NUMERIC(10, 2) NOT NULL,
    open         BOOLEAN        NOT NULL,
    created_at   TIMESTAMPTZ    NOT NULL
);

-- un candidat (e-mail) recommandé une seule fois par poste : le premier coopteur est retenu
CREATE TABLE referrals (
    id                UUID PRIMARY KEY,
    job_id            UUID          NOT NULL REFERENCES referral_jobs (id) ON DELETE CASCADE,
    referrer_id       UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    candidate_name    VARCHAR(120)  NOT NULL,
    candidate_email   VARCHAR(160)  NOT NULL,
    linkedin_url      VARCHAR(300),
    message           VARCHAR(2000) NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    internal_comment  VARCHAR(2000),
    status_changed_at TIMESTAMPTZ   NOT NULL,
    bonus_paid_at     TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_referral_candidate UNIQUE (job_id, candidate_email)
);
