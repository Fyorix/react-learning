CREATE TABLE canteen_dishes (
    id          UUID PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    category    VARCHAR(20)  NOT NULL CHECK (category IN ('STARTER', 'MAIN', 'DESSERT')),
    description VARCHAR(500),
    vegetarian  BOOLEAN      NOT NULL DEFAULT FALSE,
    allergens   VARCHAR(300),
    archived    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE UNIQUE INDEX uq_canteen_dish_name ON canteen_dishes (category, LOWER(name));

CREATE TABLE canteen_weeks (
    id         UUID PRIMARY KEY,
    week       VARCHAR(8)  NOT NULL UNIQUE,
    status     VARCHAR(20) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- un plat supprimé laisse un trou dans les plannings passés (les plannings à venir le bloquent)
CREATE TABLE canteen_day_menus (
    id          UUID PRIMARY KEY,
    week_id     UUID        NOT NULL REFERENCES canteen_weeks (id) ON DELETE CASCADE,
    day_of_week VARCHAR(10) NOT NULL,
    starter_id  UUID REFERENCES canteen_dishes (id) ON DELETE SET NULL,
    main_id     UUID REFERENCES canteen_dishes (id) ON DELETE SET NULL,
    dessert_id  UUID REFERENCES canteen_dishes (id) ON DELETE SET NULL,
    CONSTRAINT uq_canteen_day UNIQUE (week_id, day_of_week)
);
