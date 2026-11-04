-- Prepare nullable source fields only. Data conversion and retirement are separate.
ALTER TABLE mes_material_issue
    ADD COLUMN source_mode VARCHAR(20) NULL COMMENT '领料来源；未知保持NULL',
    ADD COLUMN void_result_status VARCHAR(20) NULL COMMENT '已发生的领料撤销结果';
