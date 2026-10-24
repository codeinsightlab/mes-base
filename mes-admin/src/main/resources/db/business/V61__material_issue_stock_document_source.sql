-- 生产领料草稿冻结与正式领料事实关联；历史单据不回填、不补扣库存。
ALTER TABLE wm_stock_document
    ADD COLUMN source_material_issue_id BIGINT NULL COMMENT '正式生产领料事实ID，审核时由服务端关联',
    ADD COLUMN source_request_json TEXT NULL COMMENT '生产领料草稿原请求冻结JSON，仅服务端使用',
    ADD INDEX idx_stock_document_material_issue (source_material_issue_id);
