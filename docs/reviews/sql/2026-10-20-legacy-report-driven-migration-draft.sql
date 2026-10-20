-- 待用户确认的正式业务库 Flyway 草案；本轮未执行，也未放入自动扫描目录。
-- 确认后使用届时尚未占用的业务迁移版本；不修改已有 V1 至 V39。
-- 仅在已核验的目标业务库执行；没有工厂字段、外部连接、库存操作或历史触发器修改。
-- 仅主键承担幂等竞争；其他索引为普通索引。所有业务写入仍需服务层事务和历史只读门禁。
CREATE TABLE mes_legacy_migration_batch (
    id VARCHAR(64) NOT NULL COMMENT '迁移批次身份',
    rule_version VARCHAR(64) NOT NULL COMMENT '转换规则版本',
    snapshot_hash CHAR(64) NOT NULL COMMENT '冻结输入文件的哈希摘要',
    source_instance VARCHAR(64) NOT NULL COMMENT '来源实例的稳定身份',
    source_schema VARCHAR(64) NOT NULL COMMENT '来源数据库名称',
    status VARCHAR(32) NOT NULL COMMENT '批次准备、授权、完成或失败状态',
    approved_by_user_id BIGINT NULL COMMENT '样板写入授权的系统用户标识',
    approved_at DATETIME(6) NULL COMMENT '样板写入授权时间',
    created_at DATETIME(6) NOT NULL COMMENT '批次登记时间',
    finished_at DATETIME(6) NULL COMMENT '批次结束时间',
    result_summary JSON NULL COMMENT '处理数量、检查点、失败及保护核对摘要',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='旧生产历史转换批次及阶段授权记录';

CREATE TABLE mes_legacy_source_mapping (
    id CHAR(64) NOT NULL COMMENT '来源稳定身份的哈希主键，不包含批次及规则版本',
    source_instance VARCHAR(64) NOT NULL COMMENT '来源实例的稳定身份',
    source_schema VARCHAR(64) NOT NULL COMMENT '来源数据库名称',
    source_table VARCHAR(64) NOT NULL COMMENT '来源表名或明确标记的归并对象类型',
    source_pk VARCHAR(512) NOT NULL COMMENT '原始主键或规范化的归并复合键',
    source_hash CHAR(64) NOT NULL COMMENT '冻结来源内容的哈希摘要，重跑变化必须报冲突',
    batch_id VARCHAR(64) NOT NULL COMMENT '首次建立映射的迁移批次身份',
    rule_version VARCHAR(64) NOT NULL COMMENT '首次实施的转换规则版本',
    source_workorder_code VARCHAR(128) NULL COMMENT '原始工单编号，保留冲突及超长值以供核查',
    target_workorder_id BIGINT NULL COMMENT '目标系统分配的新工单主键',
    target_execution_id BIGINT NULL COMMENT '目标系统分配的新生产执行主键',
    target_report_id BIGINT NULL COMMENT '目标系统分配的新报工主键',
    target_entity_type VARCHAR(64) NULL COMMENT '映射的目标实体类型',
    target_entity_id BIGINT NULL COMMENT '工艺、节点或其他已证明目标实体的主键',
    status VARCHAR(32) NOT NULL COMMENT '预留、完成、失败、冲突或待核实状态',
    source_payload JSON NOT NULL COMMENT '原始业务字段、外部引用及原始人员时间快照',
    conversion_payload JSON NOT NULL COMMENT '推导值、关系依据、数量口径及缺失冲突说明',
    created_at DATETIME(6) NOT NULL COMMENT '映射建立时间',
    PRIMARY KEY (id),
    KEY idx_legacy_batch (batch_id),
    KEY idx_legacy_workorder (target_workorder_id),
    KEY idx_legacy_execution (target_execution_id),
    KEY idx_legacy_report (target_report_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='旧生产来源到新业务事实的稳定映射及待核实证据';
