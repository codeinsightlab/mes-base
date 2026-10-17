-- LOCAL库存动作与余额同事务；存量库存保留真实期初余额，不为历史虚构日常入库。
CREATE TABLE wm_stock_action (
 id VARCHAR(36) NOT NULL COMMENT '稳定请求UUID及不可变库存动作主键',
 request_hash CHAR(64) NOT NULL COMMENT '规范命令及可信操作者摘要',
 stock_id BIGINT NOT NULL COMMENT '受控库存余额记录主键',
 material_id BIGINT NOT NULL COMMENT '动作发生时的物料主键',
 unit_id BIGINT NOT NULL COMMENT '动作冻结的物料基本计量单位主键',
 warehouse_id BIGINT NOT NULL COMMENT '动作发生的仓库主键',
 batch_no VARCHAR(128) NULL COMMENT '冻结材料或产出批次号',
 bin_code VARCHAR(64) NULL COMMENT '冻结仓库内库位编码',
 action_type VARCHAR(24) NOT NULL COMMENT '期初建账、原料入库、领料、退料、调整、成品接收或逆向动作编码',
 quantity_delta DECIMAL(20,6) NOT NULL COMMENT '本次实存余额有符号变动量',
 available_delta DECIMAL(20,6) NOT NULL COMMENT '本次可用余额有符号变动量',
 quantity_before DECIMAL(20,6) NOT NULL COMMENT '动作前真实实存余额，保留历史期初依据',
 quantity_after DECIMAL(20,6) NOT NULL COMMENT '动作成功后的实存余额',
 available_before DECIMAL(20,6) NOT NULL COMMENT '动作前可用余额，历史空值按实存解释',
 available_after DECIMAL(20,6) NOT NULL COMMENT '动作成功后的可用余额',
 source_type VARCHAR(24) NOT NULL COMMENT '来源业务类型：库存业务、生产领料或生产交接',
 source_id VARCHAR(64) NULL COMMENT '原业务单据或冻结投产事实身份',
 reference_action_id VARCHAR(36) NULL COMMENT '逆向操作明确关联的原库存动作主键',
 execution_id BIGINT NULL COMMENT '生产来源的执行单元主键，普通入库为空',
 operator_user_id BIGINT NOT NULL COMMENT '可信登录的仓储动作操作者',
 occurred_at DATETIME(6) NOT NULL COMMENT '本次实际业务动作时间',
 recorded_at DATETIME(6) NOT NULL COMMENT '服务端记录时间',
 reason VARCHAR(500) NOT NULL COMMENT '本次仓储业务依据及调整或逆向原因',
 PRIMARY KEY(id),
 KEY idx_stock_action_stock(stock_id,recorded_at),
 KEY idx_stock_action_source(source_type,source_id),
 KEY idx_stock_action_reverse(reference_action_id),
 KEY idx_stock_action_execution(execution_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本地仓储不可变收发存动作事实';
CREATE TRIGGER wm_stock_action_no_update BEFORE UPDATE ON wm_stock_action
 FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Stock action facts are immutable';
CREATE TRIGGER wm_stock_action_no_delete BEFORE DELETE ON wm_stock_action
 FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Stock action facts are immutable';
