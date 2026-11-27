-- 5.37 Base de connaissances
CREATE TABLE kb_roles (
    user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE kb_articles (
    id                UUID PRIMARY KEY,
    slug              VARCHAR(120) NOT NULL UNIQUE,
    created_by        UUID REFERENCES users (id) ON DELETE SET NULL,
    published_version INT,
    published_at      TIMESTAMPTZ,
    views             INT          NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL
);

-- chaque enregistrement crée une version numérotée
CREATE TABLE kb_versions (
    id             UUID PRIMARY KEY,
    article_id     UUID          NOT NULL REFERENCES kb_articles (id) ON DELETE CASCADE,
    number         INT           NOT NULL,
    title          VARCHAR(200)  NOT NULL,
    category       VARCHAR(60)   NOT NULL,
    tags           VARCHAR(300)  NOT NULL DEFAULT '',
    content        TEXT          NOT NULL,
    status         VARCHAR(20)   NOT NULL,
    author_id      UUID REFERENCES users (id) ON DELETE SET NULL,
    review_comment VARCHAR(2000),
    reviewed_by    UUID REFERENCES users (id) ON DELETE SET NULL,
    restored_from  INT,
    created_at     TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_kb_version UNIQUE (article_id, number)
);

CREATE TABLE kb_feedback (
    article_id UUID    NOT NULL REFERENCES kb_articles (id) ON DELETE CASCADE,
    user_id    UUID    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    helpful    BOOLEAN NOT NULL,
    PRIMARY KEY (article_id, user_id)
);
