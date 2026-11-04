-- 历史转换专用元数据；业务事实仍使用现有工单、执行、路径及报工表。
CREATE TABLE mes_legacy_migration_batch (
 id VARCHAR(64) NOT NULL COMMENT '迁移批次身份',
 rule_version VARCHAR(64) NOT NULL COMMENT '审核通过的转换规则版本',
 snapshot_hash CHAR(64) NOT NULL COMMENT '审核绑定的冻结来源摘要',
 status VARCHAR(24) NOT NULL COMMENT '准备、审核、运行、完成、失败或冲突状态',
 approved_scope JSON NOT NULL COMMENT '审核的工单及流程卡范围，不允许自动扩围',
 operator_user_id BIGINT NOT NULL COMMENT '真实执行迁移的系统用户',
 created_at DATETIME(6) NOT NULL COMMENT '批次建立时间',
 finished_at DATETIME(6) NULL COMMENT '批次结束时间',
 PRIMARY KEY(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='历史生产转换批次和冻结范围';
CREATE TABLE mes_legacy_source_mapping (
 id CHAR(64) NOT NULL COMMENT '来源稳定身份摘要主键，不含批次和规则版本',
 batch_id VARCHAR(64) NOT NULL COMMENT '首次迁移的审核批次',
 parent_id CHAR(64) NULL COMMENT '所属来源映射身份，节点归执行、报工归节点',
 source_table VARCHAR(64) NOT NULL COMMENT '原始表或明确的历史归并对象类型',
 source_pk VARCHAR(512) NOT NULL COMMENT '原始主键或规范归并键，实例和数据库纳入稳定身份',
 source_hash CHAR(64) NOT NULL COMMENT '来源内容摘要，变化必须重新审核',
 rule_version VARCHAR(64) NOT NULL COMMENT '首次执行的转换规则版本',
 entity_kind VARCHAR(32) NOT NULL COMMENT '唯一权威目标实体类型，未转换质量仅为证据',
 entity_id BIGINT NULL COMMENT '唯一权威目标实体主键，未转换证据为空',
 source_payload JSON NOT NULL COMMENT '原始业务数量、人员、时间与外部引用',
 conversion_payload JSON NOT NULL COMMENT '历史投入、节点聚合转换、口径和审核依据',
 created_at DATETIME(6) NOT NULL COMMENT '映射与目标事实原子提交的建立时间',
 PRIMARY KEY(id),
 KEY idx_legacy_batch(batch_id),
 KEY idx_legacy_parent(parent_id),
 KEY idx_legacy_target(entity_kind,entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='历史来源到唯一目标实体的不可变映射与数量依据';
-- 映射与数量依据必须和不可变报工保持一致，禁止原位修改或删除。
CREATE TRIGGER mes_legacy_mapping_no_update BEFORE UPDATE ON mes_legacy_source_mapping
 FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Legacy mapping is immutable';
CREATE TRIGGER mes_legacy_mapping_no_delete BEFORE DELETE ON mes_legacy_source_mapping
 FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Legacy mapping is immutable';
