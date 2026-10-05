-- Source system extension: credential/status changes invalidate existing identity-only Redis sessions.
-- New field only; V1-V4 and all development data remain unchanged.
ALTER TABLE sys_user ADD COLUMN auth_revision BIGINT NOT NULL DEFAULT 0;
