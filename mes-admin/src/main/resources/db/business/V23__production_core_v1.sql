-- 生产主链V1：执行单路径、不可变领料提交尝试和主单当前提交引用。
-- 当前路径主键直接采用Execution本地主键；节点及尝试仅建普通索引，关系由业务事务校验。
CREATE TABLE mes_execution_path (
 id BIGINT NOT NULL COMMENT '生产执行单元主键，一执行仅一条当前路径',
 routing_id BIGINT NOT NULL COMMENT '所选路径所属确切工艺版本主键',
 path_status VARCHAR(16) NOT NULL COMMENT '路径状态，SELECTED已选择或LOCKED已确认锁定',
 revision INT NOT NULL COMMENT '路径内容修订号，防止旧选择覆盖新路径',
 selected_at DATETIME NOT NULL COMMENT '首次或最近选择路径时间',
 locked_at DATETIME NULL COMMENT '首次可信领料确认并永久锁定路径时间',
 PRIMARY KEY(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生产执行当前完整路径';
CREATE TABLE mes_execution_path_node (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '执行路径节点记录主键',
 execution_id BIGINT NOT NULL COMMENT '所属生产执行单元主键',
 sequence_no INT NOT NULL COMMENT '实际路径节点顺序，从一连续递增',
 routing_node_id BIGINT NOT NULL COMMENT '确切工艺版本中的节点主键，允许不同节点复用同工序',
 PRIMARY KEY(id),
 KEY idx_execution_path_order(execution_id,sequence_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生产执行选定路径的有序节点引用';
CREATE TABLE mes_material_issue_submission (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '领料提交尝试本地主键，派生稳定外部操作身份',
 issue_id BIGINT NOT NULL COMMENT '关联领料单主键',
 path_revision INT NOT NULL COMMENT '本次提交冻结的执行路径修订号',
 command_json LONGTEXT NOT NULL COMMENT '不含凭证的完整通用领料业务命令不可变快照',
 status VARCHAR(16) NOT NULL COMMENT 'MES提交尝试状态，SUBMITTING或SUCCEEDED或REJECTED或UNKNOWN',
 created_at DATETIME NOT NULL COMMENT '提交意图持久化时间',
 finished_at DATETIME NULL COMMENT '取得确定结果的时间，不确定时为空',
 PRIMARY KEY(id),
 KEY idx_issue_submission(issue_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='领料正式提交尝试及冻结载荷历史';
ALTER TABLE mes_material_issue
 ADD COLUMN submission_id BIGINT NULL COMMENT '当前正式提交尝试主键，历史资料保持空引用',
 MODIFY COLUMN status VARCHAR(24) NOT NULL COMMENT 'MES单据状态，草稿记录、提交中待核实拒绝、确认及作废';
