-- 第一批已批准的简单业务；不包含生产执行、库存计算或真实 ERP 接入。
-- 逻辑引用由业务服务校验，不建立物理外键。

CREATE TABLE material_category (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '本地业务主键',
    factory_id VARCHAR(64) NOT NULL COMMENT '所属工厂标识，由服务端上下文赋值',
    code VARCHAR(64) NOT NULL COMMENT '工厂内业务编码',
    name VARCHAR(255) NOT NULL COMMENT '业务名称',
    parent_id BIGINT NULL COMMENT '父分类本地主键，空值表示根分类',
    sort INTEGER NOT NULL DEFAULT 0 COMMENT '分类显示顺序',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '启用状态：ENABLED启用、DISABLED停用',
    remark VARCHAR(500) NULL COMMENT '业务备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_material_category_factory_key (factory_id,code),
    KEY idx_category_parent (factory_id,parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物料产品分类，按工厂隔离';

CREATE TABLE unit_measure (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '本地业务主键',
    factory_id VARCHAR(64) NOT NULL COMMENT '所属工厂标识，由服务端上下文赋值',
    code VARCHAR(64) NOT NULL COMMENT '工厂内业务编码',
    name VARCHAR(255) NOT NULL COMMENT '业务名称',
    symbol VARCHAR(32) NULL COMMENT '单位显示符号',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '启用状态：ENABLED启用、DISABLED停用',
    PRIMARY KEY (id),
    UNIQUE KEY uk_unit_measure_factory_key (factory_id,code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='计量单位，按工厂隔离';

CREATE TABLE workshop (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '本地业务主键',
    factory_id VARCHAR(64) NOT NULL COMMENT '所属工厂标识，由服务端上下文赋值',
    code VARCHAR(64) NOT NULL COMMENT '工厂内业务编码',
    name VARCHAR(255) NOT NULL COMMENT '业务名称',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '启用状态：ENABLED启用、DISABLED停用',
    remark VARCHAR(500) NULL COMMENT '业务备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_workshop_factory_key (factory_id,code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车间，按工厂隔离';

CREATE TABLE workstation (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '本地业务主键',
    factory_id VARCHAR(64) NOT NULL COMMENT '所属工厂标识，由服务端上下文赋值',
    code VARCHAR(64) NOT NULL COMMENT '工厂内业务编码',
    name VARCHAR(255) NOT NULL COMMENT '业务名称',
    workshop_id BIGINT NULL COMMENT '可选所属车间本地主键',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '启用状态：ENABLED启用、DISABLED停用',
    remark VARCHAR(500) NULL COMMENT '业务备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_workstation_factory_key (factory_id,code),
    KEY idx_station_workshop (factory_id,workshop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作站，按工厂隔离';

CREATE TABLE material (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '本地业务主键',
    factory_id VARCHAR(64) NOT NULL COMMENT '所属工厂标识，由服务端上下文赋值',
    code VARCHAR(64) NOT NULL COMMENT '工厂内业务编码',
    name VARCHAR(255) NOT NULL COMMENT '业务名称',
    specification VARCHAR(500) NULL COMMENT '规格型号',
    category_id BIGINT NULL COMMENT '物料分类本地主键',
    unit_id BIGINT NOT NULL COMMENT '计量单位本地主键',
    material_type VARCHAR(16) NOT NULL DEFAULT 'ITEM' COMMENT '物料产品标识：ITEM物料、PRODUCT产品',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '启用状态：ENABLED启用、DISABLED停用',
    external_id VARCHAR(128) NULL COMMENT '外部物料来源标识，不作为本地主键',
    external_code VARCHAR(128) NULL COMMENT '外部物料来源编码',
    PRIMARY KEY (id),
    UNIQUE KEY uk_material_factory_key (factory_id,code),
    KEY idx_material_category (factory_id,category_id),
    KEY idx_material_unit (factory_id,unit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物料产品，按工厂隔离';

CREATE TABLE safety_stock (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '本地业务主键',
    factory_id VARCHAR(64) NOT NULL COMMENT '所属工厂标识，由服务端上下文赋值',
    material_id BIGINT NOT NULL COMMENT '物料产品本地主键',
    min_qty DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '最低库存量，安全库存下限',
    max_qty DECIMAL(18,4) NULL COMMENT '最高库存量，空值表示未设上限',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '启用状态：ENABLED启用、DISABLED停用',
    PRIMARY KEY (id),
    UNIQUE KEY uk_safety_stock_factory_key (factory_id,material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全库存配置，按工厂隔离';
