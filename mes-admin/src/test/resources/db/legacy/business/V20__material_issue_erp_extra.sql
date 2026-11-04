-- 将领料外部事实迁入领域专用扩展表；主键同时为领料业务对象ID，不新增外键或业务唯一约束。
CREATE TABLE mes_material_issue_erp_extra (
 id BIGINT NOT NULL COMMENT '对应MES领料单ID，领域对象类型固定为领料单',
 source_execution_mode VARCHAR(20) COMMENT '单据创建时仓储执行模式',
 external_effect VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED' COMMENT '外部影响事实',
 external_binding_key VARCHAR(128) COMMENT '原ERP供应商连接身份',
 external_reference VARCHAR(128) COMMENT 'ERP外部单据主键',
 void_result_status VARCHAR(20) COMMENT '作废业务结果',
 void_execution_mode VARCHAR(20) COMMENT '作废实际来源模式',
 void_message VARCHAR(500) COMMENT '安全作废诊断摘要',
 provider VARCHAR(128) NULL COMMENT 'ERP供应商标识，本地模式为local',
 external_code VARCHAR(128) NULL COMMENT 'ERP单据编号',
 document_status VARCHAR(30) NULL COMMENT 'ERP标准业务状态，不表示查询是否确定',
 last_operation VARCHAR(30) NULL COMMENT '最近ERP协调操作',
 last_outcome VARCHAR(30) NULL COMMENT '最近操作或查询确定性结果',
 last_message VARCHAR(500) NULL COMMENT '安全核实结论，禁止保存凭证报文',
 operation_version BIGINT NOT NULL DEFAULT 0 COMMENT '本地操作版本，拒绝迟到查询覆盖',
 checked_at DATETIME NULL COMMENT '最近核实时间',
 PRIMARY KEY (id)
) COMMENT='领料单ERP外部事实与核实记录';
INSERT INTO mes_material_issue_erp_extra (id,source_execution_mode,external_effect,external_binding_key,external_reference,void_result_status,void_execution_mode,void_message,provider) SELECT id,source_execution_mode,external_effect,external_binding_key,external_reference,void_result_status,void_execution_mode,void_message,CASE WHEN source_execution_mode='LOCAL_ONLY' THEN 'local' ELSE external_binding_key END FROM mes_material_issue;
ALTER TABLE mes_material_issue
 DROP COLUMN source_execution_mode,
 DROP COLUMN external_effect,
 DROP COLUMN external_binding_key,
 DROP COLUMN external_reference,
 DROP COLUMN void_result_status,
 DROP COLUMN void_execution_mode,
 DROP COLUMN void_message;
