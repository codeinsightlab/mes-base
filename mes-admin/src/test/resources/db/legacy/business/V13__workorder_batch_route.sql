-- 生产工单第一阶段：显式批次与单批次固定路径，不含报工和库存扣减。
CREATE TABLE workorder (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'MES生产工单本地主键',
 factory_id VARCHAR(64) NOT NULL COMMENT '所属工厂标识',
 code VARCHAR(64) NOT NULL COMMENT '本厂生产工单业务编号',
 product_id BIGINT NOT NULL COMMENT '产出产品本地主键',
 planned_qty DECIMAL(20,6) NOT NULL COMMENT '工单计划生产数量，等于批次计划量合计',
 routing_id BIGINT NOT NULL COMMENT '绑定的已发布工艺确切版本主键',
 status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'MES工单状态，DRAFT草稿或RELEASED已下达',
 planned_start_at DATETIME NULL COMMENT '计划开始时间',
 planned_end_at DATETIME NULL COMMENT '计划结束时间',
 external_id VARCHAR(128) NULL COMMENT '可选外部生产单据主键',
 external_code VARCHAR(128) NULL COMMENT '可选外部生产单据编号',
 remark VARCHAR(500) NULL COMMENT '工单备注',
 revision INT NOT NULL DEFAULT 0 COMMENT '并发更新修订号',
 create_time DATETIME NOT NULL COMMENT '创建时间',
 update_time DATETIME NOT NULL COMMENT '最后更新时间',
 PRIMARY KEY(id),
 KEY idx_workorder_code(factory_id,code),
 KEY idx_workorder_product(factory_id,product_id),
 KEY idx_workorder_routing(routing_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES生产工单，绑定明确工艺版本';
CREATE TABLE production_batch (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '生产批次本地主键',
 factory_id VARCHAR(64) NOT NULL COMMENT '所属工厂标识，与主工单一致',
 workorder_id BIGINT NOT NULL COMMENT '所属主工单本地主键，工艺版本继承主单',
 batch_no VARCHAR(128) NOT NULL COMMENT '主工单内批次业务编号，可沿用ERP批号',
 planned_qty DECIMAL(20,6) NOT NULL COMMENT '批次计划生产数量',
 status VARCHAR(16) NOT NULL DEFAULT 'PLANNED' COMMENT '批次状态，PLANNED待领料或ISSUED已确认出库',
 route_status VARCHAR(16) NOT NULL DEFAULT 'UNSELECTED' COMMENT '路径状态，UNSELECTED未选，SELECTED已选，PENDING出库中，UNKNOWN待核实，LOCKED锁定',
 issue_command_id VARCHAR(128) NULL COMMENT '受信任出库尝试标识，绑定本批次当前路径及结果',
 locked_at DATETIME NULL COMMENT '出库确认成功并锁定路径时间',
 revision INT NOT NULL DEFAULT 0 COMMENT '批次并发修订号',
 remark VARCHAR(500) NULL COMMENT '批次备注',
 create_time DATETIME NOT NULL COMMENT '创建时间',
 update_time DATETIME NOT NULL COMMENT '最后更新时间',
 PRIMARY KEY(id),
 KEY idx_batch_parent(workorder_id,batch_no),
 KEY idx_batch_factory(factory_id,workorder_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生产执行批次及唯一选定路径状态';
CREATE TABLE batch_route_node (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '已选择路径节点本地主键',
 batch_id BIGINT NOT NULL COMMENT '所属生产批次主键，路径状态继承批次',
 routing_node_id BIGINT NOT NULL COMMENT '工单绑定版本中选定的工艺节点主键',
 execution_order INT NOT NULL COMMENT '批次路径顺序，从一开始连续递增',
 PRIMARY KEY(id),
 KEY idx_batch_path_order(batch_id,execution_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批次唯一完整路径的有序工艺节点引用';
