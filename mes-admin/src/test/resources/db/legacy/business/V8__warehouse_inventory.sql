-- 简单仓库资料与库存数据；唯一键按本轮用户明确授权建立。
-- 物料及仓库为逻辑引用，由业务服务校验；不建立物理外键或级联删除。
CREATE TABLE wm_warehouse (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '仓库主键',
    factory_id VARCHAR(64) NOT NULL COMMENT '所属工厂标识，由服务端当前工厂上下文填写',
    code VARCHAR(64) NOT NULL COMMENT '仓库编码',
    name VARCHAR(255) NOT NULL COMMENT '仓库名称',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '仓库状态，ENABLED启用，DISABLED停用',
    remark VARCHAR(500) NULL COMMENT '仓库备注',
    create_by VARCHAR(64) NULL COMMENT '创建人系统用户标识',
    create_time DATETIME NULL COMMENT '创建时间',
    update_by VARCHAR(64) NULL COMMENT '最后修改人系统用户标识',
    update_time DATETIME NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_warehouse_factory_code (factory_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仓库管理';

CREATE TABLE wm_inventory (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '库存主键',
    factory_id VARCHAR(64) NOT NULL COMMENT '所属工厂标识，由服务端当前工厂上下文填写',
    material_id BIGINT NOT NULL COMMENT '物料主键，逻辑引用正式物料表',
    warehouse_id BIGINT NOT NULL COMMENT '仓库主键，逻辑引用仓库管理表',
    quantity DECIMAL(18,4) NOT NULL COMMENT '库存数量',
    available_quantity DECIMAL(18,4) NULL COMMENT '可用数量，空值表示未提供',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '库存状态，ENABLED启用，DISABLED停用',
    remark VARCHAR(500) NULL COMMENT '库存备注',
    create_by VARCHAR(64) NULL COMMENT '创建人系统用户标识',
    create_time DATETIME NULL COMMENT '创建时间',
    update_by VARCHAR(64) NULL COMMENT '最后修改人系统用户标识',
    update_time DATETIME NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_factory_material_warehouse (factory_id, material_id, warehouse_id),
    KEY idx_inventory_warehouse (factory_id, warehouse_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存管理';
