-- ARCHITECTURE PROTOTYPE: Material only; apply manually to an authorized business database.
-- No factory_id: existing Factory routing isolates business databases. No UNIQUE or foreign keys.
CREATE TABLE material (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '物料主键',
    code VARCHAR(64) NOT NULL COMMENT '物料编码，同步匹配业务键',
    name VARCHAR(255) NOT NULL COMMENT '物料名称',
    specification VARCHAR(255) NULL COMMENT '物料规格',
    unit VARCHAR(32) NOT NULL COMMENT '计量单位编码',
    status VARCHAR(16) NOT NULL COMMENT '物料状态，ENABLED或DISABLED',
    external_id VARCHAR(128) NULL COMMENT '来源ERP物料标识',
    external_code VARCHAR(128) NULL COMMENT '来源ERP物料编码',
    PRIMARY KEY (id),
    KEY idx_material_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ERP同步架构试点物料，非完整物料主数据模型';
