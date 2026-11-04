-- 领料作废来源与操作记录；历史外部事实保持未核实。
ALTER TABLE mes_material_issue
 ADD COLUMN source_execution_mode VARCHAR(20) NULL COMMENT '单据创建时仓储执行模式，历史未知为空',
 ADD COLUMN external_effect VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED' COMMENT '外部影响NONE、CONFIRMED、UNKNOWN、REVERSED或历史UNVERIFIED',
 ADD COLUMN external_binding_key VARCHAR(128) NULL COMMENT '原仓储系统连接身份',
 ADD COLUMN external_reference VARCHAR(128) NULL COMMENT '原外部出库单据身份',
 ADD COLUMN void_operation_id VARCHAR(64) NULL COMMENT '稳定作废操作及外部幂等身份',
 ADD COLUMN void_from_status VARCHAR(20) NULL COMMENT '作废请求前有效状态',
 ADD COLUMN void_result_status VARCHAR(20) NULL COMMENT '作废结果SUCCEEDED、REJECTED或UNKNOWN',
 ADD COLUMN void_execution_mode VARCHAR(20) NULL COMMENT '本次作废实际执行模式',
 ADD COLUMN void_requested_at DATETIME NULL COMMENT '作废操作准备时间',
 ADD COLUMN void_finished_at DATETIME NULL COMMENT '已核实作废结果时间',
 ADD COLUMN void_message VARCHAR(500) NULL COMMENT '安全诊断摘要，不作为成功判断依据';
