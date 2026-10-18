-- 工序所属不良类型；同工序编码唯一性由父工序数据库行锁与业务服务共同保证。
CREATE TABLE operation_defect_type (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '工序不良类型本地主键',
 operation_id BIGINT NOT NULL COMMENT '所属工序主键，创建后固定',
 code VARCHAR(64) NOT NULL COMMENT '同工序内唯一的不良类型编码',
 name VARCHAR(255) NOT NULL COMMENT '不良类型名称',
 default_cost_category VARCHAR(16) NOT NULL COMMENT '默认费用类别，LABOR工费、MATERIAL料费、BOTH工费与料费',
 status VARCHAR(16) NOT NULL COMMENT '配置状态，ENABLED启用、DISABLED停用',
 sort INT NOT NULL DEFAULT 0 COMMENT '显示排序，不表达工艺次序',
 remark VARCHAR(500) NULL COMMENT '配置备注',
 revision INT NOT NULL DEFAULT 0 COMMENT '并发配置修订号',
 create_by BIGINT NOT NULL COMMENT '创建配置的系统用户主键',
 create_time DATETIME(6) NOT NULL COMMENT '服务端创建时间',
 update_by BIGINT NOT NULL COMMENT '最后修改配置的系统用户主键',
 update_time DATETIME(6) NOT NULL COMMENT '服务端最后修改时间',
 PRIMARY KEY(id),
 KEY idx_operation_defect_type_parent(operation_id,code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工序直接拥有的不良类型配置';
-- 旧工废料废原值保留；新增费用与类型信息不推断、不回填历史事实。
ALTER TABLE mes_production_defect MODIFY COLUMN waste_category VARCHAR(16) NULL COMMENT '历史工废料废类别，V2普通申报不再强制采集';
ALTER TABLE mes_production_defect ADD COLUMN defect_type_id BIGINT NULL COMMENT '申报时选择的工序不良类型主键，历史未记录可空';
ALTER TABLE mes_production_defect ADD COLUMN defect_type_code_snapshot VARCHAR(64) NULL COMMENT '申报时不良类型编码快照';
ALTER TABLE mes_production_defect ADD COLUMN defect_type_name_snapshot VARCHAR(255) NULL COMMENT '申报时不良类型名称快照';
ALTER TABLE mes_production_defect ADD COLUMN defect_type_revision INT NULL COMMENT '申报时确认的不良类型修订号';
ALTER TABLE mes_production_defect ADD COLUMN cost_category VARCHAR(16) NULL COMMENT '申报时确定的费用类别，历史未知保持空';
ALTER TABLE mes_production_defect ADD COLUMN command_version VARCHAR(8) NULL COMMENT '请求指纹契约版本，V2为现场申报，空为原始契约';
ALTER TABLE mes_production_defect ADD KEY idx_defect_type(defect_type_id);
