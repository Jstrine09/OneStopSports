CREATE TABLE user_account (
    id            BIGSERIAL    PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    email         VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    avatar_url    VARCHAR(255),
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE favorite_team (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT    NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    team_id    BIGINT    NOT NULL REFERENCES team(id)         ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, team_id)
);

CREATE TABLE favorite_player (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT    NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    player_id  BIGINT    NOT NULL REFERENCES player(id)       ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, player_id)
);

CREATE INDEX idx_favorite_team_user_id   ON favorite_team(user_id);
CREATE INDEX idx_favorite_player_user_id ON favorite_player(user_id);
