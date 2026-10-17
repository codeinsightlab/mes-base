-- 来源未知的历史记录保持为空；有证据的回填由受控验收清单执行，不按当前工厂模式猜测。
ALTER TABLE workorder ADD COLUMN source VARCHAR(16) NULL COMMENT '工单可信创建来源：LOCAL本地，EXTERNAL外部，空为历史待核实';
ALTER TABLE wm_warehouse ADD COLUMN source VARCHAR(16) NULL COMMENT '仓库可信创建来源：LOCAL本地，EXTERNAL外部，空为历史待核实';
ALTER TABLE workorder_erp_extra
 ADD COLUMN binding_key VARCHAR(128) NULL COMMENT '可信同步时冻结的ERP连接身份',
 ADD COLUMN configuration_revision BIGINT NULL COMMENT '可信同步时冻结的ERP连接配置版本';
CREATE TABLE wm_warehouse_erp_extra (
 id BIGINT NOT NULL COMMENT '对应MES仓库主键',
 external_id VARCHAR(128) NOT NULL COMMENT 'ERP仓库稳定业务标识',
 provider VARCHAR(128) NOT NULL COMMENT '可信同步的ERP供应商标识',
 binding_key VARCHAR(128) NOT NULL COMMENT '可信同步时冻结的ERP连接身份',
 configuration_revision BIGINT NOT NULL COMMENT '可信同步时冻结的ERP连接配置版本',
 PRIMARY KEY (id)
) COMMENT='外部仓库可信身份映射';
-- 允许经核实为空来源补登记，既有非空来源不允许普通更新切换或清空。
DELIMITER $$
CREATE TRIGGER workorder_source_immutable BEFORE UPDATE ON workorder FOR EACH ROW
BEGIN
 IF OLD.source IS NOT NULL AND NOT (OLD.source <=> NEW.source) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '工单来源创建后不可更改';
 END IF;
END$$
CREATE TRIGGER wm_warehouse_source_immutable BEFORE UPDATE ON wm_warehouse FOR EACH ROW
BEGIN
 IF OLD.source IS NOT NULL AND NOT (OLD.source <=> NEW.source) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '仓库来源创建后不可更改';
 END IF;
END$$
DELIMITER ;
