-- 工序能力与执行节点派工：人员引用中心系统用户，业务关系按工厂独立业务库隔离。
CREATE TABLE mes_operation_user (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '工序人员能力关系主键',
 operation_id BIGINT NOT NULL COMMENT '当前业务库工序定义主键',
 user_id BIGINT NOT NULL COMMENT '中心系统用户主键',
 remark VARCHAR(500) NULL COMMENT '能力配置备注',
 create_by VARCHAR(64) NULL COMMENT '能力配置操作人系统用户标识',
 create_time DATETIME NULL COMMENT '能力配置创建时间',
 PRIMARY KEY(id),
 UNIQUE KEY uk_operation_user(operation_id,user_id),
 KEY idx_operation_user_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工序可操作人员能力配置';
CREATE TABLE mes_execution_assignment (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '执行节点人员派工关系主键',
 execution_id BIGINT NOT NULL COMMENT '当前业务库生产执行单元主键',
 routing_node_id BIGINT NOT NULL COMMENT '执行所用确切工艺版本节点主键',
 user_id BIGINT NOT NULL COMMENT '被派工中心系统用户主键',
 assigned_by BIGINT NOT NULL COMMENT '可信登录派工人系统用户主键',
 assigned_at DATETIME NOT NULL COMMENT '服务端派工时间',
 remark VARCHAR(500) NULL COMMENT '派工备注',
 PRIMARY KEY(id),
 UNIQUE KEY uk_execution_node_user(execution_id,routing_node_id,user_id),
 KEY idx_assignment_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生产执行工艺节点人员派工';
