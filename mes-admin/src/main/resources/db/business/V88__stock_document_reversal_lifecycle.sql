-- V88: Add stock-document reversal audit fields.
-- Schema-only change: retain existing stock documents, stock actions, and Flyway history.
ALTER TABLE wm_stock_document
    ADD COLUMN reversed_by BIGINT NULL COMMENT '正式撤销用户主键',
    ADD COLUMN reversed_at DATETIME(6) NULL COMMENT '正式撤销时间',
    ADD COLUMN reversal_reason VARCHAR(500) NULL COMMENT '正式撤销原因',
    ADD COLUMN reversal_request_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '正式撤销操作UUID',
    ADD COLUMN reversal_request_hash CHAR(64) NULL COMMENT '正式撤销请求与操作者摘要',
    ADD UNIQUE KEY uk_stock_document_reversal_request (reversal_request_id);
