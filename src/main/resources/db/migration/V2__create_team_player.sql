CREATE TABLE team (
    id         BIGSERIAL PRIMARY KEY,
    league_id  BIGINT       NOT NULL REFERENCES league(id),
    name       VARCHAR(100) NOT NULL,
    short_name VARCHAR(20),
    crest_url  VARCHAR(255),
    stadium    VARCHAR(100),
    country    VARCHAR(100),
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE player (
    id              BIGSERIAL PRIMARY KEY,
    team_id         BIGINT       NOT NULL REFERENCES team(id),
    name            VARCHAR(100) NOT NULL,
    position        VARCHAR(50),
    nationality     VARCHAR(100),
    date_of_birth   DATE,
    jersey_number   INTEGER,
    photo_url       VARCHAR(255),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_team_league_id   ON team(league_id);
CREATE INDEX idx_player_team_id   ON player(team_id);
