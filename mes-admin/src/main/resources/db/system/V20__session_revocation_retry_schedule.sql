-- 待处理表示尚未持久确认完成，Redis撤销可能已经成功。
ALTER TABLE sys_session_revocation
    ADD COLUMN next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次允许重试撤销交付的时间';
CREATE INDEX ix_session_revocation_due ON sys_session_revocation(next_attempt_at,user_id);
