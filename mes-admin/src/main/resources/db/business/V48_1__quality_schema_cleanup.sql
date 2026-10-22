-- 当前质量流程清理：申报记录不保存尚未执行的处置去向，处置事实保存实际去向。
-- 执行前由维护流程备份完整账本；不删除事实行，不重写既有动作、身份或幂等摘要。
ALTER TABLE mes_production_defect
 DROP COLUMN destination_type;
ALTER TABLE mes_production_defect DROP COLUMN target_node_id;
ALTER TABLE mes_production_defect DROP COLUMN target_warehouse_id;
ALTER TABLE mes_production_defect DROP COLUMN destination_remark;
ALTER TABLE mes_production_defect DROP COLUMN command_version;

-- 旧分配/独立返工结果模型已退役；当前只有核实、返回节点及转仓。
ALTER TABLE mes_quality_treatment DROP INDEX idx_quality_treatment_parent;
ALTER TABLE mes_quality_treatment DROP COLUMN parent_id;
ALTER TABLE mes_quality_treatment DROP COLUMN scrap_qty;
ALTER TABLE mes_quality_treatment DROP COLUMN processed_qty;
ALTER TABLE mes_quality_treatment DROP COLUMN recovered_qty;
ALTER TABLE mes_quality_treatment DROP COLUMN failed_qty;
-- 当前正式处理始终由登录操作者即时执行，不复制确认人和同值发生时间。
ALTER TABLE mes_quality_treatment DROP COLUMN confirmed_by;
ALTER TABLE mes_quality_treatment DROP COLUMN occurred_at;
ALTER TABLE mes_quality_treatment RENAME COLUMN rework_qty TO returned_qty;
ALTER TABLE mes_quality_treatment
 MODIFY COLUMN returned_qty DECIMAL(20,6) NOT NULL DEFAULT 0 COMMENT 'RETURN_NODE返回申报节点并恢复正常报工额度的数量';
ALTER TABLE mes_quality_treatment
 MODIFY COLUMN action_type VARCHAR(32) NOT NULL COMMENT '当前质量事实动作：ASSESS核实、RETURN_NODE返回节点、TRANSFER_WAREHOUSE入库';
ALTER TABLE mes_quality_treatment
 MODIFY COLUMN recorded_at DATETIME(6) NOT NULL COMMENT '后台即时执行本次质量操作的服务端时间';
ALTER TABLE mes_quality_treatment COMMENT = '不良归属核实、返工返回节点及质量入库不可变处理流水';
