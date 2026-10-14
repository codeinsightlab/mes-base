-- 生产执行身份：按业务库隔离，独立于材料批次和工单默认工艺。
CREATE TABLE mes_production_execution (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '生产执行单元本地主键',
 execution_no VARCHAR(64) NOT NULL COMMENT '稳定执行编号，由服务端根据本地主键生成且不可修改',
 workorder_id BIGINT NOT NULL COMMENT '归属生产工单本地主键',
 product_id BIGINT NOT NULL COMMENT '执行产出产品本地主键，从工单确定后保持稳定',
 routing_id BIGINT NOT NULL COMMENT '本次执行所用已发布工艺确切版本主键',
 target_qty DECIMAL(20,6) NOT NULL COMMENT '计划执行数量，不代表最终产出数量',
 status VARCHAR(20) NOT NULL DEFAULT 'READY' COMMENT '执行资料状态：READY待执行、RUNNING执行中、COMPLETED已结束、CANCELLED已取消',
 start_time DATETIME NULL COMMENT '实际开始时间，可由管理员订正',
 end_time DATETIME NULL COMMENT '实际结束时间，可由管理员订正',
 remark VARCHAR(500) NULL COMMENT '生产执行备注',
 create_by VARCHAR(64) NULL COMMENT '创建系统用户标识',
 create_time DATETIME NULL COMMENT '创建时间',
 update_by VARCHAR(64) NULL COMMENT '最后订正系统用户标识',
 update_time DATETIME NULL COMMENT '最后订正时间',
 PRIMARY KEY (id),
 KEY idx_execution_no (execution_no),
 KEY idx_execution_workorder (workorder_id),
 KEY idx_execution_product (product_id),
 KEY idx_execution_routing (routing_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生产执行单元的稳定生产身份';
-- 旧领料资料保持空关联，保留原工艺和流程卡字段。
ALTER TABLE mes_material_issue ADD COLUMN execution_id BIGINT NULL COMMENT '关联生产执行身份，可空以兼容历史管理员资料' AFTER workorder_id;
ALTER TABLE mes_material_issue ADD KEY idx_issue_execution (execution_id);
