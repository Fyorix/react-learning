-- 5.17 Kanban d'équipe
CREATE TABLE kanban_boards (
    id         UUID PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    owner_id   UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE TABLE kanban_board_members (
    board_id UUID NOT NULL REFERENCES kanban_boards (id) ON DELETE CASCADE,
    user_id  UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (board_id, user_id)
);

CREATE TABLE kanban_columns (
    id         UUID PRIMARY KEY,
    board_id   UUID        NOT NULL REFERENCES kanban_boards (id) ON DELETE CASCADE,
    name       VARCHAR(60) NOT NULL,
    position   INT         NOT NULL,
    wip_limit  INT CHECK (wip_limit IS NULL OR wip_limit >= 1),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE kanban_cards (
    id          UUID PRIMARY KEY,
    column_id   UUID          NOT NULL REFERENCES kanban_columns (id),
    title       VARCHAR(150)  NOT NULL,
    description VARCHAR(4000),
    assignee_id UUID REFERENCES users (id) ON DELETE SET NULL,
    due_date    DATE,
    labels      VARCHAR(300)  NOT NULL DEFAULT '',
    position    INT           NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL
);

CREATE TABLE kanban_moves (
    id             UUID PRIMARY KEY,
    card_id        UUID        NOT NULL REFERENCES kanban_cards (id) ON DELETE CASCADE,
    from_column_id UUID,
    to_column_id   UUID        NOT NULL,
    from_name      VARCHAR(60),
    to_name        VARCHAR(60) NOT NULL,
    author_id      UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_kanban_columns_board ON kanban_columns (board_id, position);
CREATE INDEX idx_kanban_cards_column ON kanban_cards (column_id, position);
