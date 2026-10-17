-- 工单作废只终止后续业务，不修改既有生产与仓储事实。
CREATE TABLE mes_workorder_cancellation (
 id VARCHAR(36) NOT NULL COMMENT '正式作废命令的稳定请求UUID及取消事实主键',
 workorder_id BIGINT NOT NULL COMMENT '被终止后续生产活动的工单主键',
 request_fingerprint CHAR(64) NOT NULL COMMENT '规范请求与可信操作者的幂等指纹',
 reason VARCHAR(500) NOT NULL COMMENT '必填工单作废原因',
 operator_user_id BIGINT NOT NULL COMMENT '服务端确定的作废操作系统用户主键',
 recorded_at DATETIME(6) NOT NULL COMMENT '服务端作废事实记录时间',
 previous_status VARCHAR(16) NOT NULL COMMENT '作废前工单状态快照',
 source_snapshot VARCHAR(16) NULL COMMENT '原可信来源快照，空仍为历史未知来源',
 previous_revision INT NOT NULL COMMENT '作废前工单乐观版本号',
 PRIMARY KEY(id), KEY idx_workorder_cancellation_order(workorder_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='终止后续生产且不逆向历史事实的不可变工单作废记录';
CREATE TRIGGER mes_workorder_cancellation_no_update BEFORE UPDATE ON mes_workorder_cancellation
 FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Workorder cancellation facts are immutable';
CREATE TRIGGER mes_workorder_cancellation_no_delete BEFORE DELETE ON mes_workorder_cancellation
 FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Workorder cancellation facts are immutable';
