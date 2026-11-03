ALTER TABLE workorder
    ADD COLUMN bom_build_id VARCHAR(36) NULL COMMENT '同一次BOM展阶工单创建集合标识' AFTER material_updated_at,
    ADD COLUMN bom_build_parent_workorder_id BIGINT NULL COMMENT '本次展阶创建的父工单ID' AFTER bom_build_id,
    ADD COLUMN bom_build_source_line_id BIGINT NULL COMMENT '来源父BOM组件行ID' AFTER bom_build_parent_workorder_id,
    ADD COLUMN bom_build_path VARCHAR(2048) NULL COMMENT '来源BOM展开路径' AFTER bom_build_source_line_id,
    ADD COLUMN bom_build_request_id VARCHAR(64) NULL COMMENT '展阶提交幂等请求标识，仅根工单保存' AFTER bom_build_path,
    ADD COLUMN bom_build_request_hash CHAR(64) NULL COMMENT '展阶提交内容指纹，仅根工单保存' AFTER bom_build_request_id,
    ADD UNIQUE KEY uk_workorder_bom_build_request (bom_build_request_id),
    ADD KEY idx_workorder_bom_build_path (bom_build_id, bom_build_path(255)),
    ADD KEY idx_workorder_bom_build_parent (bom_build_parent_workorder_id);
