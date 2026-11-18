-- Users, and every monitor now belongs to exactly one of them.

CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,   -- a BCrypt hash (60 chars), never the password itself
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Emails are unique ignoring case: "Ann@x.com" and "ann@x.com" are the same person.
CREATE UNIQUE INDEX uq_users_email ON users (lower(email));

-- Monitors created before users existed need an owner. If there are any, a placeholder account takes them.
-- Its password hash '!' is not valid BCrypt, so nobody can ever log in as it; re-assign the rows by hand if you care.
-- On a fresh database there are no monitors, so no placeholder is created.
INSERT INTO users (email, password_hash)
SELECT 'legacy@pulsewatch.local', '!'
WHERE EXISTS (SELECT 1 FROM monitors);

ALTER TABLE monitors ADD COLUMN user_id BIGINT REFERENCES users (id) ON DELETE CASCADE;
UPDATE monitors SET user_id = (SELECT id FROM users WHERE email = 'legacy@pulsewatch.local');
ALTER TABLE monitors ALTER COLUMN user_id SET NOT NULL;

-- "All monitors of one user" is the list query.
CREATE INDEX idx_monitors_user ON monitors (user_id);
