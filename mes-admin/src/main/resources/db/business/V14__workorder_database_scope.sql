-- 用户确认：业务库按工厂隔离，新工单与批次不重复保存工厂字段。
-- 用户批准工单库内编号唯一，以及同一主单内批次编号唯一。
ALTER TABLE workorder DROP INDEX idx_workorder_code,
 DROP INDEX idx_workorder_product, DROP COLUMN factory_id,
 ADD UNIQUE KEY uk_workorder_code(code), ADD KEY idx_workorder_product(product_id);
ALTER TABLE production_batch DROP INDEX idx_batch_parent,
 DROP INDEX idx_batch_factory, DROP COLUMN factory_id,
 ADD UNIQUE KEY uk_batch_parent_no(workorder_id,batch_no),
 ADD KEY idx_batch_parent(workorder_id);
