-- Pending means not durably acknowledged. Redis delivery may already have succeeded.
ALTER TABLE sys_session_revocation
    ADD COLUMN next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
CREATE INDEX ix_session_revocation_due ON sys_session_revocation(next_attempt_at,user_id);
