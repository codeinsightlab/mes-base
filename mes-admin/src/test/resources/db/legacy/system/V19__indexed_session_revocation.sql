-- 认证版本与撤销记录在同一中心库事务中提交，支持持久恢复交付。
CREATE TABLE sys_session_revocation (
    user_id BIGINT NOT NULL PRIMARY KEY COMMENT '待确认会话撤销的用户编号',
    revision BIGINT NOT NULL COMMENT '本次撤销对应的认证版本',
    attempts INT NOT NULL DEFAULT 0 COMMENT '撤销交付失败次数',
    last_attempt TIMESTAMP NULL COMMENT '最近一次撤销交付失败时间',
    last_error VARCHAR(128) NULL COMMENT '不含敏感信息的撤销交付错误摘要'
) COMMENT='尚未持久确认完成的用户会话撤销记录';
