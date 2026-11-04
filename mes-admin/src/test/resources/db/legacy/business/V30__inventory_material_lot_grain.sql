-- 库存由原材料与仓库粒度扩展到批次及库位；旧唯一键会阻止同仓不同批次入账。
-- 保留全部存量记录，不新增非主键唯一约束；具体来源记录用主键精确选择并锁定。
ALTER TABLE wm_inventory
 DROP INDEX uk_inventory_material_warehouse,
 ADD INDEX idx_inventory_material_warehouse_batch(material_id,warehouse_id,batch_no);
