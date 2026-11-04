-- Prepare the formal unified production fields; never moves or deletes environment data.
ALTER TABLE wm_stock_document
MODIFY create_by VARCHAR(64) NULL,
MODIFY create_time DATETIME(6) NULL,
MODIFY update_by VARCHAR(64) NULL,
MODIFY update_time DATETIME(6) NULL;

ALTER TABLE wm_stock_document_line
MODIFY warehouse_id BIGINT NULL,
MODIFY stock_id BIGINT NULL;

ALTER TABLE wm_stock_document
ADD COLUMN production_request_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '原生产领料请求UUID；与过账请求身份分离',
ADD COLUMN production_workorder_code VARCHAR(64) NULL COMMENT '原生产工单编号快照',
ADD COLUMN production_product_id BIGINT NULL COMMENT '原生产产出产品身份快照',
ADD COLUMN production_product_code VARCHAR(64) NULL COMMENT '原生产产出品号快照',
ADD COLUMN production_routing_id BIGINT NULL COMMENT '原生产工艺版本身份',
ADD COLUMN production_request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '原生产领料请求指纹',
ADD COLUMN production_input_kind VARCHAR(24) NULL COMMENT 'PRODUCTION_INPUT或MATERIAL_SUPPLEMENT',
ADD COLUMN production_output_qty DECIMAL(20,6) NULL COMMENT '该单据唯一贡献的生产产出量',
ADD COLUMN production_source_mode VARCHAR(20) NULL COMMENT '由原可信事实迁移的生产业务来源',
ADD COLUMN production_calculation_json LONGTEXT NULL COMMENT '版本化ProductionSupply.Calculation冻结依据，仅供原请求恢复与核对',
ADD COLUMN production_request_created_at DATETIME(6) NULL COMMENT '生产领料请求建立时间，独立于单据创建时间',
ADD COLUMN production_confirmed_at DATETIME(6) NULL COMMENT '生产投入确认时间，独立于单据过账时间',
ADD COLUMN production_business_occurred_at DATETIME(6) NULL COMMENT '生产领料业务发生时间',
ADD COLUMN production_stock_applied BOOLEAN NULL COMMENT '原生产事实确认时记录的本地库存生效状态',
ADD COLUMN production_path_revision INT NULL COMMENT '确认时冻结的Execution路径版本',
ADD COLUMN production_confirmation_operation_id VARCHAR(64) NULL COMMENT '原确认操作键',
ADD COLUMN production_confirmation_command_json LONGTEXT NULL COMMENT '原确认冻结命令原文，恢复和审计专用，不参与数量计算',
ADD COLUMN production_confirmation_status VARCHAR(20) NULL COMMENT '原确认命令结果状态',
ADD COLUMN production_submission_created_at DATETIME(6) NULL COMMENT '原确认命令创建时间',
ADD COLUMN production_submission_finished_at DATETIME(6) NULL COMMENT '原确认命令完成时间',
ADD COLUMN production_confirmed_path_node_ids LONGTEXT NULL COMMENT '原确认命令冻结的Execution路径节点ID列表',
ADD COLUMN production_void_operation_id VARCHAR(64) NULL COMMENT '原撤销操作UUID',
ADD COLUMN production_void_from_status VARCHAR(20) NULL COMMENT '撤销前生产事实状态',
ADD COLUMN production_void_requested_at DATETIME(6) NULL COMMENT '生产事实撤销请求时间',
ADD COLUMN production_void_finished_at DATETIME(6) NULL COMMENT '生产事实撤销完成时间',
ADD COLUMN production_void_result_status VARCHAR(20) NULL COMMENT '生产事实撤销结果；UNKNOWN保持未知',
ADD UNIQUE KEY uk_stock_document_production_request (production_request_id);

ALTER TABLE wm_stock_document_line
    ADD COLUMN create_by VARCHAR(64) NULL,
    ADD COLUMN create_time DATETIME(6) NULL,
    ADD COLUMN update_by VARCHAR(64) NULL,
    ADD COLUMN update_time DATETIME(6) NULL;

ALTER TABLE wm_stock_document
    ADD COLUMN production_original_issue_no VARCHAR(64) NULL,
    ADD COLUMN production_original_remark VARCHAR(500) NULL,
    ADD COLUMN production_original_create_by VARCHAR(64) NULL,
    ADD COLUMN production_original_create_time DATETIME(6) NULL,
    ADD COLUMN production_original_update_by VARCHAR(64) NULL,
    ADD COLUMN production_original_update_time DATETIME(6) NULL;
