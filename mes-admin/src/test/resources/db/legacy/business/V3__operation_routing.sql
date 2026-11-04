-- 工序定义与工艺路线版本；物理业务库路由与表内工厂边界同时保留。
-- 唯一约束按本批用户授权建立；逻辑引用由同事务业务服务校验，不建立物理外键。
CREATE TABLE operation (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '工序定义本地主键',
    factory_id VARCHAR(64) NOT NULL COMMENT '所属工厂标识，由服务端上下文赋值',
    code VARCHAR(64) NOT NULL COMMENT '工厂内工序编码',
    name VARCHAR(255) NOT NULL COMMENT '工序名称',
    description VARCHAR(1000) NULL COMMENT '工艺要求或工序说明',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '定义状态：ENABLED启用、DISABLED停用',
    sort INTEGER NOT NULL DEFAULT 0 COMMENT '工序列表显示顺序',
    external_id VARCHAR(128) NULL COMMENT '可选外部来源标识，不作为本地主键',
    revision INTEGER NOT NULL DEFAULT 0 COMMENT '并发修改修订号，由服务端递增',
    PRIMARY KEY (id),
    UNIQUE KEY uk_operation_factory_code (factory_id,code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工序定义，按工厂隔离';

CREATE TABLE routing (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '工艺路线确切版本本地主键',
    factory_id VARCHAR(64) NOT NULL COMMENT '所属工厂标识，由服务端上下文赋值',
    code VARCHAR(64) NOT NULL COMMENT '工厂内路线编码，同编码标识一个版本系列',
    name VARCHAR(255) NOT NULL COMMENT '此版本的路线名称',
    version INTEGER NOT NULL DEFAULT 1 COMMENT '业务版本号，从一开始由服务端分配',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT '版本状态：DRAFT草稿、PUBLISHED发布、DISABLED停用',
    description VARCHAR(1000) NULL COMMENT '此版本的工艺路线说明',
    external_id VARCHAR(128) NULL COMMENT '可选外部路线标识，不作为本地主键',
    source_version VARCHAR(128) NULL COMMENT '可选来源版本标签，不决定本地业务版本号',
    revision INTEGER NOT NULL DEFAULT 0 COMMENT '并发修改修订号，由服务端递增',
    published_at DATETIME(6) NULL COMMENT '首次发布时刻，停用后仍保留',
    PRIMARY KEY (id),
    UNIQUE KEY uk_routing_factory_version (factory_id,code,version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工艺路线版本，发布后内容冻结';

CREATE TABLE routing_step (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '路线步骤本地主键',
    routing_id BIGINT NOT NULL COMMENT '所属路线确切版本主键，工厂边界继承父级',
    sequence INTEGER NOT NULL COMMENT '版本内执行序号，从一开始连续排列',
    operation_id BIGINT NOT NULL COMMENT '引用的工序定义本地主键',
    operation_code VARCHAR(64) NULL COMMENT '发布时冻结的工序编码，草稿可为空',
    operation_name VARCHAR(255) NULL COMMENT '发布时冻结的工序名称，草稿可为空',
    is_key BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否关键工序，一个版本最多一个关键点',
    description VARCHAR(1000) NULL COMMENT '此步骤的补充工艺说明',
    PRIMARY KEY (id),
    UNIQUE KEY uk_routing_step_sequence (routing_id,sequence),
    UNIQUE KEY uk_routing_step_operation (routing_id,operation_id),
    KEY idx_routing_step_operation (operation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='路线版本的有序工序步骤';

CREATE TABLE routing_product (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '路线适用物料产品关系本地主键',
    routing_id BIGINT NOT NULL COMMENT '所属路线确切版本主键，工厂边界继承父级',
    material_id BIGINT NOT NULL COMMENT '引用的物料产品本地主键',
    material_code VARCHAR(64) NULL COMMENT '发布时冻结的物料产品编码，草稿可为空',
    material_name VARCHAR(255) NULL COMMENT '发布时冻结的物料产品名称，草稿可为空',
    PRIMARY KEY (id),
    UNIQUE KEY uk_routing_product_material (routing_id,material_id),
    KEY idx_routing_product_material (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='路线版本适用的物料产品集合';
