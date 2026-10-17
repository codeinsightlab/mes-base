-- 正式 LOCAL 其他收发单据；余额仍由 LocalStockService 唯一写入。
ALTER TABLE wm_stock_action
 ADD COLUMN document_line_id BIGINT NULL COMMENT '正式仓储单据明细主键，其他来源为空',
 ADD COLUMN operation_id VARCHAR(36) NULL COMMENT '多行正式过账共享的原始操作UUID，旧动作为空';
CREATE TABLE wm_stock_document (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '仓储单据主键',
 document_no VARCHAR(64) NOT NULL COMMENT '服务端生成的正式单据编号',
 document_type VARCHAR(24) NOT NULL COMMENT '单据类型：OTHER_RECEIPT其他入库或OTHER_ISSUE其他出库',
 status VARCHAR(16) NOT NULL COMMENT '单据状态：DRAFT草稿、POSTED已过账、CANCELLED已作废',
 reason VARCHAR(500) NOT NULL COMMENT '业务原因及实物处理依据',
 remark VARCHAR(500) NULL COMMENT '单据备注',
 source_workorder_id BIGINT NULL COMMENT '关联已作废工单主键，创建后不变',
 create_by VARCHAR(64) NOT NULL COMMENT '服务端确定的创建用户主键',
 create_time DATETIME(6) NOT NULL COMMENT '服务端创建时间',
 update_by VARCHAR(64) NOT NULL COMMENT '服务端确定的最后维护用户主键',
 update_time DATETIME(6) NOT NULL COMMENT '服务端最后维护时间',
 posted_by BIGINT NULL COMMENT '正式过账用户主键',
 posted_at DATETIME(6) NULL COMMENT '正式过账时间',
 cancelled_by BIGINT NULL COMMENT '草稿作废用户主键',
 cancelled_at DATETIME(6) NULL COMMENT '草稿作废时间',
 revision BIGINT NOT NULL COMMENT '主单及明细共享的乐观版本号',
 post_request_id VARCHAR(36) NULL COMMENT '成功过账的原始操作UUID，重放沿用原身份',
 post_request_hash CHAR(64) NULL COMMENT '成功过账命令与原始操作者的冻结摘要',
 PRIMARY KEY(id),
 KEY idx_stock_document_state(document_type,status,id),
 KEY idx_stock_document_number(document_no),
 KEY idx_stock_document_request(post_request_id),
 KEY idx_stock_document_order(source_workorder_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LOCAL正式其他入库与其他出库单据头';

CREATE TABLE wm_stock_document_line (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '仓储单据明细主键',
 document_id BIGINT NOT NULL COMMENT '所属仓储主单主键，由服务端固定',
 line_no INT NOT NULL COMMENT '主单锁内分配且创建后固定的明细行号',
 material_id BIGINT NOT NULL COMMENT '库存身份中的物料主键',
 warehouse_id BIGINT NOT NULL COMMENT '库存身份中的LOCAL仓库主键',
 stock_id BIGINT NOT NULL COMMENT '现有受控库存来源主键，先建立零余额来源',
 batch_no VARCHAR(128) NULL COMMENT '库存来源冻结批次号',
 bin_code VARCHAR(64) NULL COMMENT '库存来源冻结库位编码',
 unit_id BIGINT NOT NULL COMMENT '物料基本单位主键',
 quantity DECIMAL(20,6) NOT NULL COMMENT '本行正式收发正数量',
 remark VARCHAR(500) NULL COMMENT '明细备注及实物确认依据',
 stock_action_id VARCHAR(36) NULL COMMENT '同事务追加的不可变库存动作主键',
 revision BIGINT NOT NULL COMMENT '明细编辑乐观版本号',
 PRIMARY KEY(id),
 KEY idx_stock_document_line_parent(document_id,line_no),
 KEY idx_stock_document_line_stock(stock_id),
 KEY idx_stock_document_line_material(material_id),
 KEY idx_stock_document_line_warehouse(warehouse_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LOCAL正式仓储单据明细及不可变库存动作关联';
