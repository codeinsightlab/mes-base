-- 不良自身闭环，不参与生产数量、库存及外部ERP过账。
CREATE TABLE mes_production_defect (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '不良申报版本主键',
 declaration_id VARCHAR(36) NOT NULL COMMENT '稳定的不良申报身份',
 revision_no INT NOT NULL COMMENT '完整版本号，从一递增',
 record_action VARCHAR(16) NOT NULL COMMENT '版本动作，CREATE申报、REVISE订正、VOID作废',
 revision_reason VARCHAR(500) NULL COMMENT '订正或作废的留痕原因',
 execution_id BIGINT NOT NULL COMMENT '生产执行单元主键，申报后固定',
 affected_node_id BIGINT NOT NULL COMMENT '数量影响工艺节点主键，申报后固定',
 discovered_node_id BIGINT NOT NULL COMMENT '发现不良的实际路径工艺节点',
 responsible_node_id BIGINT NULL COMMENT '责任工艺节点，未认定可空',
 defect_qty DECIMAL(20,6) NOT NULL COMMENT '不良产品数量，正数',
 waste_category VARCHAR(16) NOT NULL COMMENT '不良类别，PROCESS工废或MATERIAL料废',
 defect_reason VARCHAR(500) NOT NULL COMMENT '不良原因',
 responsible_user_id BIGINT NULL COMMENT '责任系统用户主键，未认定可空',
 responsibility_remark VARCHAR(500) NULL COMMENT '责任认定说明',
 destination_type VARCHAR(16) NOT NULL COMMENT '去向类型，ROUTING_NODE退工序或WAREHOUSE退仓库',
 target_node_id BIGINT NULL COMMENT '退回的实际路径上游节点',
 target_warehouse_id BIGINT NULL COMMENT '退回的已启用仓库主键',
 destination_remark VARCHAR(500) NULL COMMENT '仅登记的处置去向说明',
 request_key VARCHAR(64) COLLATE utf8mb4_bin NOT NULL COMMENT '当前业务库内唯一的请求幂等标识',
 request_fingerprint VARCHAR(64) NOT NULL COMMENT '命令类型与规范业务参数的摘要',
 reported_by BIGINT NOT NULL COMMENT '原始申报人的可信系统用户主键',
 occurred_at DATETIME(6) NOT NULL COMMENT '不良实际发生时间',
 recorded_by BIGINT NOT NULL COMMENT '本版本录入人的可信系统用户主键',
 recorded_at DATETIME(6) NOT NULL COMMENT '本版本服务端记录时间',
 PRIMARY KEY(id),
 UNIQUE KEY uk_defect_revision(declaration_id,revision_no),
 UNIQUE KEY uk_defect_request(request_key),
 KEY idx_defect_execution_node(execution_id,affected_node_id),
 KEY idx_defect_responsible(responsible_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不良申报不可变完整版本账本';
CREATE TRIGGER mes_defect_no_update BEFORE UPDATE ON mes_production_defect
 FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Production defect ledger is immutable';
CREATE TRIGGER mes_defect_no_delete BEFORE DELETE ON mes_production_defect
 FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Production defect ledger is immutable';
