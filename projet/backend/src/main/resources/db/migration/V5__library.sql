CREATE TABLE library_books (
    id         UUID PRIMARY KEY,
    title      VARCHAR(200) NOT NULL,
    author     VARCHAR(150) NOT NULL,
    genre      VARCHAR(50)  NOT NULL,
    isbn       VARCHAR(20),
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE TABLE library_loans (
    id          UUID PRIMARY KEY,
    book_id     UUID        NOT NULL REFERENCES library_books (id) ON DELETE CASCADE,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    borrowed_at TIMESTAMPTZ NOT NULL,
    due_date    DATE        NOT NULL,
    returned_at TIMESTAMPTZ,
    extended    BOOLEAN     NOT NULL DEFAULT FALSE
);

-- un seul emprunt actif par livre
CREATE UNIQUE INDEX uq_library_active_loan ON library_loans (book_id) WHERE returned_at IS NULL;

CREATE TABLE library_queue (
    id             UUID PRIMARY KEY,
    book_id        UUID        NOT NULL REFERENCES library_books (id) ON DELETE CASCADE,
    user_id        UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    joined_at      TIMESTAMPTZ NOT NULL,
    reserved_until TIMESTAMPTZ,
    CONSTRAINT uq_library_queue UNIQUE (book_id, user_id)
);
