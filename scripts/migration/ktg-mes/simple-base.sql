-- 第一批简单业务最小转换器。只处理已授权的离线测试源，不连接公司生产库。
-- 执行前将 source_test 替换为准备好的 Source 测试库，并显式设置 @target_factory。
-- Source 快照无 factory_id：每次只导入一个经确认的工厂数据集；有该字段的来源需先筛选。
-- 本地自增主键重新分配，所有引用按工厂和编码映射；不把 ERP 或旧库 ID 当新库主键。
START TRANSACTION;
INSERT INTO material_category(factory_id,code,name,parent_id,sort,status,remark)
SELECT @target_factory,item_type_code,item_type_name,NULL,COALESCE(order_num,0),IF(enable_flag='Y','ENABLED','DISABLED'),remark FROM source_test.md_item_type;
UPDATE material_category child
JOIN source_test.md_item_type old_child ON old_child.item_type_code=child.code
JOIN source_test.md_item_type old_parent ON old_parent.item_type_id=old_child.parent_type_id
JOIN material_category parent ON parent.factory_id=child.factory_id AND parent.code=old_parent.item_type_code
SET child.parent_id=parent.id WHERE child.factory_id=@target_factory;
INSERT INTO unit_measure(factory_id,code,name,symbol,status)
SELECT @target_factory,measure_code,measure_name,NULL,IF(enable_flag='Y','ENABLED','DISABLED') FROM source_test.md_unit_measure;
INSERT INTO workshop(factory_id,code,name,status,remark)
SELECT @target_factory,workshop_code,workshop_name,IF(enable_flag='Y','ENABLED','DISABLED'),remark FROM source_test.md_workshop;
INSERT INTO workstation(factory_id,code,name,workshop_id,status,remark)
SELECT @target_factory,s.workstation_code,s.workstation_name,w.id,IF(s.enable_flag='Y','ENABLED','DISABLED'),s.remark FROM source_test.md_workstation s
LEFT JOIN source_test.md_workshop ow ON ow.workshop_id=s.workshop_id
LEFT JOIN workshop w ON w.factory_id=@target_factory AND w.code=ow.workshop_code;
INSERT INTO material(factory_id,code,name,specification,category_id,unit_id,material_type,status,external_id,external_code)
SELECT @target_factory,s.item_code,s.item_name,s.specification,c.id,u.id,s.item_or_product,IF(s.enable_flag='Y','ENABLED','DISABLED'),NULL,NULL FROM source_test.md_item s
LEFT JOIN source_test.md_item_type oc ON oc.item_type_id=s.item_type_id
LEFT JOIN material_category c ON c.factory_id=@target_factory AND c.code=oc.item_type_code
LEFT JOIN unit_measure u ON u.factory_id=@target_factory AND u.code=s.unit_of_measure;
-- 只有设置了安全库存的物料生成配置；采购参数和库存计算不迁入。
INSERT INTO safety_stock(factory_id,material_id,min_qty,max_qty,status)
SELECT @target_factory,m.id,CAST(COALESCE(s.min_stock,0) AS DECIMAL(18,4)),CAST(s.max_stock AS DECIMAL(18,4)),'ENABLED' FROM source_test.md_item s
JOIN material m ON m.factory_id=@target_factory AND m.code=s.item_code WHERE s.safe_stock_flag='Y';
COMMIT;
-- 导入前需检查孤儿分类/单位/车间、重复编码、分类环路、负库存和最大值小于最小值。
-- 导入不是同步 Upsert：重复执行由唯一约束拒绝，不用 INSERT IGNORE 掩盖冲突。
