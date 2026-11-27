CREATE TABLE quizzes (
    id           UUID PRIMARY KEY,
    title        VARCHAR(150) NOT NULL,
    description  VARCHAR(1000),
    published    BOOLEAN      NOT NULL DEFAULT FALSE,
    published_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL
);

CREATE TABLE quiz_questions (
    id       UUID PRIMARY KEY,
    quiz_id  UUID         NOT NULL REFERENCES quizzes (id) ON DELETE CASCADE,
    label    VARCHAR(500) NOT NULL,
    position INT          NOT NULL
);

CREATE TABLE quiz_answers (
    id          UUID PRIMARY KEY,
    question_id UUID         NOT NULL REFERENCES quiz_questions (id) ON DELETE CASCADE,
    label       VARCHAR(300) NOT NULL,
    correct     BOOLEAN      NOT NULL,
    position    INT          NOT NULL
);

CREATE TABLE quiz_attempts (
    id           UUID PRIMARY KEY,
    quiz_id      UUID        NOT NULL REFERENCES quizzes (id) ON DELETE CASCADE,
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    score        INT         NOT NULL,
    total        INT         NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_quiz_attempt UNIQUE (quiz_id, user_id)
);
