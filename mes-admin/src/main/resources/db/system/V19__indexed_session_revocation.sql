-- Durable delivery; credential revision and this record commit in the same center transaction.
CREATE TABLE sys_session_revocation (
    user_id BIGINT NOT NULL PRIMARY KEY,
    revision BIGINT NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    last_attempt TIMESTAMP NULL,
    last_error VARCHAR(128) NULL
);
