-- 报工数量采用不可变追加流水，工厂隔离沿用业务库路由。
CREATE TABLE mes_production_report (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '报工数量流水主键',
 execution_id BIGINT NOT NULL COMMENT '生产执行单元主键',
 routing_node_id BIGINT NOT NULL COMMENT '执行锁定路径中的确切工艺节点主键',
 record_type VARCHAR(20) NOT NULL COMMENT '流水类型，REPORT原始报工或ADJUSTMENT数量调整',
 quantity_delta DECIMAL(20,6) NOT NULL COMMENT '本次加工完成数量变化，调整允许负数',
 reference_report_id BIGINT NULL COMMENT '调整所引用的原始报工流水主键',
 operator_user_id BIGINT NOT NULL COMMENT '实际生产操作人员的中心系统用户主键',
 recorded_by BIGINT NOT NULL COMMENT '本次提交人的可信登录系统用户主键',
 report_time DATETIME(6) NOT NULL COMMENT '实际生产发生时间，调整继承原始报工时间',
 workstation_id BIGINT NULL COMMENT '实际生产工作站引用，可不填写',
 reason VARCHAR(500) NOT NULL COMMENT '管理员补录或数量订正原因',
 create_time DATETIME(6) NOT NULL COMMENT '服务端生成的流水创建时间',
 PRIMARY KEY(id),
 KEY idx_report_execution_node(execution_id,routing_node_id),
 KEY idx_report_reference(reference_report_id),
 KEY idx_report_operator_time(operator_user_id,report_time),
 KEY idx_report_created(create_time),
 CONSTRAINT ck_report_kind_quantity CHECK (
   (record_type='REPORT' AND quantity_delta>0 AND reference_report_id IS NULL)
   OR (record_type='ADJUSTMENT' AND quantity_delta<>0 AND reference_report_id IS NOT NULL)),
 CONSTRAINT ck_report_reason CHECK (CHAR_LENGTH(TRIM(reason))>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可变生产报工数量事实与管理员调整流水';
-- 服务和数据库均禁止改写或物理删除数量事实。
CREATE TRIGGER mes_report_no_update BEFORE UPDATE ON mes_production_report
 FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Production report ledger is immutable';
CREATE TRIGGER mes_report_no_delete BEFORE DELETE ON mes_production_report
 FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Production report ledger is immutable';
