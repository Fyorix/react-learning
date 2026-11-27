-- 5.30 Tournoi de baby-foot
CREATE TABLE tournament_tournaments (
    id                    UUID PRIMARY KEY,
    name                  VARCHAR(100) NOT NULL,
    format                VARCHAR(30)  NOT NULL,
    mode                  VARCHAR(20)  NOT NULL,
    registration_deadline TIMESTAMPTZ  NOT NULL,
    organizer_id          UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status                VARCHAR(20)  NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL
);

-- une inscription = un joueur (simple) ou une équipe de deux (double)
CREATE TABLE tournament_entries (
    id            UUID PRIMARY KEY,
    tournament_id UUID        NOT NULL REFERENCES tournament_tournaments (id) ON DELETE CASCADE,
    player1_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    player2_id    UUID REFERENCES users (id) ON DELETE CASCADE,
    seed          INT,
    created_at    TIMESTAMPTZ NOT NULL
);

CREATE TABLE tournament_matches (
    id            UUID PRIMARY KEY,
    tournament_id UUID        NOT NULL REFERENCES tournament_tournaments (id) ON DELETE CASCADE,
    round         INT         NOT NULL,
    position      INT         NOT NULL,
    entry_a_id    UUID REFERENCES tournament_entries (id) ON DELETE CASCADE,
    entry_b_id    UUID REFERENCES tournament_entries (id) ON DELETE CASCADE,
    score_a       INT,
    score_b       INT,
    reported_by   UUID REFERENCES users (id) ON DELETE SET NULL,
    status        VARCHAR(20) NOT NULL,
    winner_id     UUID REFERENCES tournament_entries (id) ON DELETE SET NULL,
    confirmed_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_tournament_match_slot UNIQUE (tournament_id, round, position)
);

CREATE TABLE tournament_ratings (
    user_id    UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    elo        INT         NOT NULL,
    played     INT         NOT NULL,
    wins       INT         NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
