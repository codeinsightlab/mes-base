-- 生产资源基础目录：仅保留生产执行所需投影，不迁入维护和人事档案。
CREATE TABLE equipment (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '设备本地主键',
    factory_id VARCHAR(64) NOT NULL COMMENT '所属工厂标识，由服务端上下文赋值',
    code VARCHAR(64) NOT NULL COMMENT '设备编码',
    name VARCHAR(255) NOT NULL COMMENT '设备名称',
    model VARCHAR(255) NULL COMMENT '设备规格型号',
    workstation_id BIGINT NULL COMMENT '可选所属工作站本地主键',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '启用状态：ENABLED启用、DISABLED停用',
    remark VARCHAR(500) NULL COMMENT '设备备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_equipment_factory_code (factory_id, code),
    KEY idx_equipment_workstation (factory_id, workstation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备';

CREATE TABLE personnel (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '生产人员本地主键',
    factory_id VARCHAR(64) NOT NULL COMMENT '所属工厂标识，由服务端上下文赋值',
    code VARCHAR(64) NOT NULL COMMENT '生产人员编码',
    name VARCHAR(255) NOT NULL COMMENT '生产人员姓名',
    user_id BIGINT NULL COMMENT '可选关联中心库账号标识，不授予工厂权限',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '启用状态：ENABLED启用、DISABLED停用',
    PRIMARY KEY (id),
    UNIQUE KEY uk_personnel_factory_code (factory_id, code),
    KEY idx_personnel_user (factory_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生产人员';
