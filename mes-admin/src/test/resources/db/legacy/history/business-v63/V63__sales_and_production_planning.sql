-- 新增独立的销售订单与生产计划基础资料，不建立销售需求到计划或工单的自动关联。
-- 工厂范围由独立业务数据库提供；本迁移不在业务表保存工厂标识，不建立物理外键。
-- 编号及父子行号由数据库唯一索引保证并发写入时不会重复。

CREATE TABLE mes_customer (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '客户本地主键',
    code VARCHAR(64) NOT NULL COMMENT '当前工厂业务库内唯一的客户编码',
    name VARCHAR(255) NOT NULL COMMENT '客户名称',
    short_name VARCHAR(128) NULL COMMENT '客户简称',
    contact_person VARCHAR(128) NULL COMMENT '客户联系人姓名',
    contact_phone VARCHAR(64) NULL COMMENT '客户联系电话',
    address VARCHAR(500) NULL COMMENT '客户联系地址',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '客户启用状态，ENABLED启用或DISABLED停用',
    source_type VARCHAR(16) NOT NULL DEFAULT 'LOCAL' COMMENT '客户来源，LOCAL本地或EXTERNAL外部ERP',
    external_id VARCHAR(128) NULL COMMENT '外部系统客户身份标识，不作为本地主键',
    remark VARCHAR(500) NULL COMMENT '客户业务备注',
    revision INT NOT NULL DEFAULT 0 COMMENT '乐观并发修订号',
    created_by BIGINT NULL COMMENT '创建客户的系统用户主键',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '客户资料创建时间',
    updated_by BIGINT NULL COMMENT '最近修改客户资料的系统用户主键',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '客户资料最近修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_customer_code (code),
    KEY idx_mes_customer_name (name),
    KEY idx_mes_customer_status (status),
    KEY idx_mes_customer_source_external (source_type, external_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售客户基础资料';

CREATE TABLE mes_sales_order (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '销售订单本地主键',
    order_no VARCHAR(64) NOT NULL COMMENT '当前工厂业务库内唯一的销售订单编号',
    customer_id BIGINT NOT NULL COMMENT '关联客户本地主键',
    order_date DATE NOT NULL COMMENT '销售订单业务日期',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT '销售订单状态，本期仅允许DRAFT草稿',
    source_type VARCHAR(16) NOT NULL DEFAULT 'LOCAL' COMMENT '订单来源，LOCAL本地或EXTERNAL外部ERP',
    external_id VARCHAR(128) NULL COMMENT '外部系统订单身份标识，不作为本地主键',
    remark VARCHAR(500) NULL COMMENT '销售订单业务备注',
    revision INT NOT NULL DEFAULT 0 COMMENT '乐观并发修订号',
    created_by BIGINT NULL COMMENT '创建订单的系统用户主键',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '销售订单创建时间',
    updated_by BIGINT NULL COMMENT '最近修改订单的系统用户主键',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '销售订单最近修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_sales_order_no (order_no),
    KEY idx_mes_sales_order_customer_date (customer_id, order_date),
    KEY idx_mes_sales_order_status_date (status, order_date),
    KEY idx_mes_sales_order_source_external (source_type, external_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售订单草稿主单';

CREATE TABLE mes_sales_order_item (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '销售订单明细本地主键',
    sales_order_id BIGINT NOT NULL COMMENT '所属销售订单主键',
    line_no INT NOT NULL COMMENT '订单内唯一的明细行号',
    material_id BIGINT NOT NULL COMMENT '订购产品或物料本地主键',
    unit_id BIGINT NOT NULL COMMENT '订单数量使用的物料基本计量单位主键快照',
    ordered_qty DECIMAL(20,6) NOT NULL COMMENT '精确十进制订购数量，必须大于零',
    required_date DATE NULL COMMENT '客户要求交付日期',
    remark VARCHAR(500) NULL COMMENT '订单明细业务备注',
    revision INT NOT NULL DEFAULT 0 COMMENT '乐观并发修订号',
    created_by BIGINT NULL COMMENT '创建明细的系统用户主键',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '销售订单明细创建时间',
    updated_by BIGINT NULL COMMENT '最近修改明细的系统用户主键',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '销售订单明细最近修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_sales_order_item_line (sales_order_id, line_no),
    KEY idx_mes_sales_order_item_material (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售订单产品明细';

CREATE TABLE mes_production_plan (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '生产计划本地主键',
    plan_no VARCHAR(64) NOT NULL COMMENT '当前工厂业务库内唯一的生产计划编号',
    plan_date DATE NOT NULL COMMENT '生产计划业务日期',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT '生产计划状态，本期仅允许DRAFT草稿',
    remark VARCHAR(500) NULL COMMENT '生产计划业务备注',
    revision INT NOT NULL DEFAULT 0 COMMENT '乐观并发修订号',
    created_by BIGINT NULL COMMENT '创建计划的系统用户主键',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '生产计划创建时间',
    updated_by BIGINT NULL COMMENT '最近修改计划的系统用户主键',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '生产计划最近修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_production_plan_no (plan_no),
    KEY idx_mes_production_plan_status_date (status, plan_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='独立创建的生产计划草稿主单';

CREATE TABLE mes_production_plan_item (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '生产计划明细本地主键',
    plan_id BIGINT NOT NULL COMMENT '所属生产计划主键',
    line_no INT NOT NULL COMMENT '计划内唯一的明细行号',
    material_id BIGINT NOT NULL COMMENT '生产产品本地主键',
    planned_qty DECIMAL(20,6) NOT NULL COMMENT '精确十进制计划生产数量，必须大于零',
    planned_start_at DATETIME(6) NULL COMMENT '计划开始时间',
    planned_end_at DATETIME(6) NULL COMMENT '计划完成时间，不得早于开始时间',
    priority INT NOT NULL DEFAULT 0 COMMENT '计划明细排序优先级，数值越小越优先',
    remark VARCHAR(500) NULL COMMENT '生产计划明细业务备注',
    revision INT NOT NULL DEFAULT 0 COMMENT '乐观并发修订号',
    created_by BIGINT NULL COMMENT '创建明细的系统用户主键',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '生产计划明细创建时间',
    updated_by BIGINT NULL COMMENT '最近修改明细的系统用户主键',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '生产计划明细最近修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_production_plan_item_line (plan_id, line_no),
    KEY idx_mes_production_plan_item_material (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生产计划产品明细';
