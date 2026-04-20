CREATE TABLE sport (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    slug       VARCHAR(50)  NOT NULL UNIQUE,
    icon_url   VARCHAR(255),
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE league (
    id         BIGSERIAL PRIMARY KEY,
    sport_id   BIGINT       NOT NULL REFERENCES sport(id),
    name       VARCHAR(100) NOT NULL,
    country    VARCHAR(100),
    logo_url   VARCHAR(255),
    season     VARCHAR(20),
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_league_sport_id ON league(sport_id);
