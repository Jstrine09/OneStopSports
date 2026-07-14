-- V10: Server-side store for refresh tokens (token security hardening, QA finding S3).
--
-- Background: the access token (a short-lived JWT) is now kept only in the browser's
-- memory and sent as a Bearer header. To keep users logged in across page reloads without
-- putting a long-lived credential in JavaScript-readable storage, we issue a separate
-- REFRESH token that lives in an httpOnly cookie (so an XSS payload can't read it) and is
-- tracked here in the database.
--
-- Why a database row per token (instead of a self-contained JWT refresh token)?
--   • Revocation: logging out — or detecting a stolen token — must be able to invalidate
--     a specific refresh token immediately. A stateless JWT can't be un-issued; a row can.
--   • Rotation: every time a refresh token is used we issue a new one and mark the old
--     one revoked, so a token is only ever valid once.
--
-- Security note: we store only a SHA-256 HASH of the token, never the raw value. If this
-- table leaked, the hashes can't be replayed as cookies (the server hashes the incoming
-- cookie and compares). This mirrors how we never store raw passwords.
--
-- Like the other migrations, this runs against PostgreSQL only — Flyway is disabled in the
-- H2 test profile, where Hibernate builds the schema directly from the entity mapping.

CREATE TABLE refresh_token (
    id         BIGSERIAL PRIMARY KEY,
    -- The SHA-256 hash (hex) of the raw token value. 64 hex chars for SHA-256.
    -- UNIQUE because each raw token is looked up by its hash on every refresh.
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    -- Which user this token belongs to. ON DELETE CASCADE so deleting a user cleans up
    -- their tokens automatically (matches how favourites cascade in V3).
    user_id    BIGINT      NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    -- When this token stops being accepted, regardless of the revoked flag.
    expires_at TIMESTAMP   NOT NULL,
    -- Set true on logout, on rotation (the old token), or if reuse is detected.
    revoked    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Speeds up "revoke / list all of a user's tokens" (used on logout-everywhere and
-- reuse detection). The token_hash lookup is already covered by the UNIQUE constraint.
CREATE INDEX idx_refresh_token_user ON refresh_token(user_id);
