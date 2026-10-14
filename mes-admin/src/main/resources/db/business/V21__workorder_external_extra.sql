-- 保留旧外部生产单据标识，不推定供应商、真实ERP业务状态或外部成功。
CREATE TABLE workorder_erp_extra (
 id BIGINT NOT NULL COMMENT '对应MES工单ID，业务对象类型固定为工单',
 external_id VARCHAR(128) NULL COMMENT '历史外部生产单据标识，来源尚未核实',
 external_code VARCHAR(128) NULL COMMENT '历史外部生产单据编号，来源尚未核实',
 provider VARCHAR(128) NULL COMMENT '已验证的ERP供应商，历史未知保持为空',
 PRIMARY KEY (id)
) COMMENT='工单外部生产单据身份扩展事实';
INSERT INTO workorder_erp_extra(id,external_id,external_code)
 SELECT id,external_id,external_code FROM workorder;
ALTER TABLE workorder DROP COLUMN external_id, DROP COLUMN external_code;
