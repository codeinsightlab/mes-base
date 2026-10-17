-- 本地单材料需求及生产闭环事实；只建立结构，不转换历史配置或测试数据。
CREATE TABLE mes_workorder_material (
 id BIGINT NOT NULL COMMENT '工单主键，同时标识该工单唯一的本地用料权威',
 material_id BIGINT NOT NULL COMMENT '单材料需求对应物料主键',
 unit_id BIGINT NOT NULL COMMENT '需求冻结的物料基本单位主键',
 total_required_qty DECIMAL(20,6) NOT NULL COMMENT '计划数量对应的材料总需求量',
 planned_qty DECIMAL(20,6) NOT NULL COMMENT '用料比例采用的工单计划数量依据',
 warehouse_ids VARCHAR(2000) NOT NULL COMMENT '允许领料仓库主键的规范JSON数组',
 work_center VARCHAR(100) NOT NULL COMMENT '本地生产工作中心说明',
 revision BIGINT NOT NULL COMMENT '用料变更乐观版本号',
 status VARCHAR(16) NOT NULL COMMENT '本地需求启用状态，停用后不回退配置权威',
 updated_by BIGINT NOT NULL COMMENT '最后维护的可信系统用户主键',
 updated_at DATETIME(6) NOT NULL COMMENT '最后维护时间',
 PRIMARY KEY(id), KEY idx_workorder_material_material(material_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单本地单材料用料需求';
CREATE TABLE mes_quality_release (
 id VARCHAR(36) NOT NULL COMMENT '放行或撤回命令的稳定请求UUID',
 execution_id BIGINT NOT NULL COMMENT '质量放行所属生产执行主键',
 sequence_no BIGINT NOT NULL COMMENT '执行锁内单调增加的放行事实序号',
 action_type VARCHAR(16) NOT NULL COMMENT '放行累计授权或撤回原授权动作',
 quantity DECIMAL(20,6) NOT NULL COMMENT '本次明确授权的累计有效数量而非追加增量',
 basis_hash CHAR(64) NOT NULL COMMENT '实际路径、产出及质量事实的冻结摘要',
 reference_id VARCHAR(36) NULL COMMENT '本次替代或撤回的上一条放行事实主键',
 request_hash CHAR(64) NOT NULL COMMENT '规范命令与可信操作者指纹',
 operator_user_id BIGINT NOT NULL COMMENT '执行本次放行命令的系统用户主键',
 recorded_at DATETIME(6) NOT NULL COMMENT '服务端记录放行时间',
 reason VARCHAR(500) NOT NULL COMMENT '放行或撤回依据',
 PRIMARY KEY(id), KEY idx_quality_release_execution(execution_id,sequence_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='执行级不可变质量累计放行事实';
CREATE TABLE mes_warehouse_acceptance (
 id VARCHAR(36) NOT NULL COMMENT '仓储接收或逆向命令的稳定请求UUID',
 receipt_id VARCHAR(36) NOT NULL COMMENT '原生产正向交接事实主键',
 execution_id BIGINT NOT NULL COMMENT '原交接所属执行主键',
 product_id BIGINT NOT NULL COMMENT '接收的执行产品主键',
 stock_id BIGINT NOT NULL COMMENT '受控成品库存来源主键',
 quantity_delta DECIMAL(20,6) NOT NULL COMMENT '实际接收为正、逆向归还为负的数量',
 reference_id VARCHAR(36) NULL COMMENT '逆向操作明确关联的原仓储接收事实主键',
 stock_action_id VARCHAR(36) NOT NULL COMMENT '同事务不可变库存动作主键',
 request_hash CHAR(64) NOT NULL COMMENT '规范命令与可信操作者指纹',
 operator_user_id BIGINT NOT NULL COMMENT '实际接收或逆向处理系统用户主键',
 recorded_at DATETIME(6) NOT NULL COMMENT '服务端接收事实记录时间',
 reason VARCHAR(500) NOT NULL COMMENT '实际接收依据或差额、逆向原因',
 PRIMARY KEY(id), KEY idx_acceptance_receipt(receipt_id), KEY idx_acceptance_execution(execution_id),
 KEY idx_acceptance_reference(reference_id), KEY idx_acceptance_stock(stock_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生产交接后的不可变成品仓储实际接收事实';
CREATE TABLE mes_execution_completion (
 id VARCHAR(36) NOT NULL COMMENT '正式完工命令的稳定请求UUID',
 execution_id BIGINT NOT NULL COMMENT '正式完工的生产执行主键，由执行链锁防止重复完工',
 effective_qty DECIMAL(20,6) NOT NULL COMMENT '完工时末道有效产出快照',
 released_qty DECIMAL(20,6) NOT NULL COMMENT '完工时可信质量放行数量快照',
 receipt_qty DECIMAL(20,6) NOT NULL COMMENT '完工时生产净交接数量快照',
 accepted_qty DECIMAL(20,6) NOT NULL COMMENT '完工时仓库净接收数量快照',
 basis_hash CHAR(64) NOT NULL COMMENT '完工时生产质量路径与数量依据摘要',
 request_hash CHAR(64) NOT NULL COMMENT '完工请求与可信操作者指纹',
 operator_user_id BIGINT NOT NULL COMMENT '正式完工操作系统用户主键',
 recorded_at DATETIME(6) NOT NULL COMMENT '实际正式完工时间',
 reason VARCHAR(500) NOT NULL COMMENT '正式完工确认依据',
 PRIMARY KEY(id), KEY idx_completion_execution(execution_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='严格生产完工不可变事实';
CREATE TRIGGER mes_quality_release_no_update BEFORE UPDATE ON mes_quality_release
 FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Production closure facts are immutable';
CREATE TRIGGER mes_quality_release_no_delete BEFORE DELETE ON mes_quality_release
 FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Production closure facts are immutable';
CREATE TRIGGER mes_warehouse_acceptance_no_update BEFORE UPDATE ON mes_warehouse_acceptance
 FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Production closure facts are immutable';
CREATE TRIGGER mes_warehouse_acceptance_no_delete BEFORE DELETE ON mes_warehouse_acceptance
 FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Production closure facts are immutable';
CREATE TRIGGER mes_execution_completion_no_update BEFORE UPDATE ON mes_execution_completion
 FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Production closure facts are immutable';
CREATE TRIGGER mes_execution_completion_no_delete BEFORE DELETE ON mes_execution_completion
 FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Production closure facts are immutable';
