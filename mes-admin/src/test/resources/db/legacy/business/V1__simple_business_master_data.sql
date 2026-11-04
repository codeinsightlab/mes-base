-- 第一轮业务能力迁移：仅建立单位、车间、检测项、缺陷字典和设备点检项目。
-- 在业务库使用独立的 flyway_business_schema_history，不混用中心库迁移历史。
-- 工厂身份由服务端当前上下文填写；没有默认工厂，不新增权限或路由机制。
-- 除主键外只建立普通索引；编码索引不承诺并发唯一性，不建立物理外键。

CREATE TABLE md_unit_measure (
    measure_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '计量单位主键',
    factory_id VARCHAR(64) NOT NULL COMMENT '所属工厂标识，由服务端当前工厂上下文填写',
    measure_code VARCHAR(64) NOT NULL COMMENT '计量单位编码',
    measure_name VARCHAR(255) NOT NULL COMMENT '计量单位名称',
    primary_flag CHAR(1) NOT NULL DEFAULT 'Y' COMMENT '主单位标识，Y为主单位，N为换算单位',
    primary_id BIGINT NULL COMMENT '关联主单位的主键，主单位自身可不填写',
    change_rate DECIMAL(18,6) NULL COMMENT '相对于主单位的换算比例',
    enable_flag CHAR(1) NOT NULL DEFAULT 'Y' COMMENT '启用标识，Y为启用，N为停用',
    remark VARCHAR(500) NULL COMMENT '计量单位补充说明',
    create_by VARCHAR(64) NULL COMMENT '创建人账号标识',
    create_time DATETIME NULL COMMENT '创建时间',
    update_by VARCHAR(64) NULL COMMENT '最近修改人账号标识',
    update_time DATETIME NULL COMMENT '最近修改时间',
    PRIMARY KEY (measure_id),
    KEY idx_unit_factory_code (factory_id, measure_code),
    KEY idx_unit_factory_enabled (factory_id, enable_flag),
    KEY idx_unit_factory_primary (factory_id, primary_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工厂计量单位与主单位换算资料';

CREATE TABLE md_workshop (
    workshop_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '车间主键',
    factory_id VARCHAR(64) NOT NULL COMMENT '所属工厂标识，由服务端当前工厂上下文填写',
    workshop_code VARCHAR(64) NOT NULL COMMENT '车间编码',
    workshop_name VARCHAR(255) NOT NULL COMMENT '车间名称',
    area DECIMAL(18,2) NULL COMMENT '车间面积，沿用来源资料的计量口径',
    charge VARCHAR(64) NULL COMMENT '车间负责人说明，保留来源文本，不推断为人员编号',
    dept_id BIGINT NULL COMMENT '关联中心人员目录中的部门编号，允许暂未关联',
    enable_flag CHAR(1) NOT NULL DEFAULT 'Y' COMMENT '启用标识，Y为启用，N为停用',
    remark VARCHAR(500) NULL COMMENT '车间补充说明',
    create_by VARCHAR(64) NULL COMMENT '创建人账号标识',
    create_time DATETIME NULL COMMENT '创建时间',
    update_by VARCHAR(64) NULL COMMENT '最近修改人账号标识',
    update_time DATETIME NULL COMMENT '最近修改时间',
    PRIMARY KEY (workshop_id),
    KEY idx_workshop_factory_code (factory_id, workshop_code),
    KEY idx_workshop_factory_enabled (factory_id, enable_flag),
    KEY idx_workshop_factory_dept (factory_id, dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工厂车间基础资料，不包含外部系统绑定';

CREATE TABLE qc_index (
    index_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '质量检测项主键',
    factory_id VARCHAR(64) NOT NULL COMMENT '所属工厂标识，由服务端当前工厂上下文填写',
    index_code VARCHAR(64) NOT NULL COMMENT '质量检测项编码',
    index_name VARCHAR(255) NOT NULL COMMENT '质量检测项名称',
    index_type VARCHAR(64) NOT NULL COMMENT '检测项类型编码，保留来源字典语义',
    qc_tool VARCHAR(255) NULL COMMENT '用于检测的工具说明',
    remark VARCHAR(500) NULL COMMENT '质量检测项补充说明',
    create_by VARCHAR(64) NULL COMMENT '创建人账号标识',
    create_time DATETIME NULL COMMENT '创建时间',
    update_by VARCHAR(64) NULL COMMENT '最近修改人账号标识',
    update_time DATETIME NULL COMMENT '最近修改时间',
    PRIMARY KEY (index_id),
    KEY idx_qc_index_factory_code (factory_id, index_code),
    KEY idx_qc_index_factory_type (factory_id, index_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工厂质量检测项字典，不承载检验流程';

CREATE TABLE qc_defect (
    defect_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '常见质量缺陷主键',
    factory_id VARCHAR(64) NOT NULL COMMENT '所属工厂标识，由服务端当前工厂上下文填写',
    defect_code VARCHAR(64) NOT NULL COMMENT '缺陷编码，来源系统使用流水号生成',
    defect_name VARCHAR(500) NOT NULL COMMENT '缺陷描述',
    index_type VARCHAR(64) NOT NULL COMMENT '对应检测项类型编码，保留来源字典语义',
    defect_level VARCHAR(64) NOT NULL COMMENT '缺陷等级编码，保留来源字典语义',
    remark VARCHAR(500) NULL COMMENT '缺陷字典补充说明',
    create_by VARCHAR(64) NULL COMMENT '创建人账号标识',
    create_time DATETIME NULL COMMENT '创建时间',
    update_by VARCHAR(64) NULL COMMENT '最近修改人账号标识',
    update_time DATETIME NULL COMMENT '最近修改时间',
    PRIMARY KEY (defect_id),
    KEY idx_qc_defect_factory_code (factory_id, defect_code),
    KEY idx_qc_defect_factory_type (factory_id, index_type),
    KEY idx_qc_defect_factory_level (factory_id, defect_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工厂常见质量缺陷字典，不包含责任扣款';

CREATE TABLE dv_subject (
    subject_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '设备点检保养项目主键',
    factory_id VARCHAR(64) NOT NULL COMMENT '所属工厂标识，由服务端当前工厂上下文填写',
    subject_code VARCHAR(64) NOT NULL COMMENT '设备点检保养项目编码',
    subject_name VARCHAR(255) NULL COMMENT '设备点检保养项目名称，允许仅填写项目内容',
    subject_type VARCHAR(64) NULL COMMENT '点检或保养类型编码，保留来源字典语义',
    subject_content VARCHAR(500) NOT NULL COMMENT '需要执行的点检或保养内容',
    subject_standard VARCHAR(255) NULL COMMENT '项目执行或判定标准',
    enable_flag CHAR(1) NOT NULL DEFAULT 'Y' COMMENT '启用标识，Y为启用，N为停用',
    remark VARCHAR(500) NULL COMMENT '点检保养项目补充说明',
    create_by VARCHAR(64) NULL COMMENT '创建人账号标识',
    create_time DATETIME NULL COMMENT '创建时间',
    update_by VARCHAR(64) NULL COMMENT '最近修改人账号标识',
    update_time DATETIME NULL COMMENT '最近修改时间',
    PRIMARY KEY (subject_id),
    KEY idx_subject_factory_code (factory_id, subject_code),
    KEY idx_subject_factory_enabled (factory_id, enable_flag),
    KEY idx_subject_factory_type (factory_id, subject_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工厂设备点检保养项目字典，不承载计划与执行记录';
