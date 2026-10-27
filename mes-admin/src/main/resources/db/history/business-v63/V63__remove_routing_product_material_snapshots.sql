-- 工艺适用产品仅保存物料关联；编码、名称由统一业务目录读取当前物料资料。
-- 不改工艺与物料的关联身份，不修改工序快照或历史生产事实。
ALTER TABLE routing_product DROP COLUMN material_code;
ALTER TABLE routing_product DROP COLUMN material_name;
