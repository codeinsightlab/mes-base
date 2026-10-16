-- 原品号与批次旧码只在唯一绑定时解析，不在不同产出产品之间猜测或取第一条。
ALTER TABLE mes_production_scan_identity
 ADD COLUMN product_code VARCHAR(64) COLLATE utf8mb4_0900_bin NULL COMMENT '已绑定身份中的产出品号原文，大小写及前导零保持不变',
 ADD COLUMN material_code VARCHAR(64) COLLATE utf8mb4_0900_bin NULL COMMENT '已绑定身份中的投入材料品号原文',
 ADD COLUMN material_lot_code VARCHAR(128) COLLATE utf8mb4_0900_bin NULL COMMENT '已绑定身份中的投入材料批次原文',
 ADD INDEX idx_scan_material_lot(material_code,material_lot_code);
-- 新增绑定均有同一准备事务产生的冻结用料事实；补齐已有绑定的检索字段，不产生投产额度。
UPDATE mes_production_scan_identity b
 JOIN mes_material_issue_input f ON f.execution_id=b.execution_id
 SET b.product_code=JSON_UNQUOTE(JSON_EXTRACT(f.calculation_json,'$.order.productCode')),
     b.material_code=JSON_UNQUOTE(JSON_EXTRACT(f.calculation_json,'$.stock.materialCode')),
     b.material_lot_code=JSON_UNQUOTE(JSON_EXTRACT(f.calculation_json,'$.stock.lotCode'))
 WHERE b.product_code IS NULL AND JSON_VALID(f.calculation_json)
   AND JSON_EXTRACT(f.calculation_json,'$.order.productCode') IS NOT NULL
   AND JSON_EXTRACT(f.calculation_json,'$.stock.materialCode') IS NOT NULL
   AND JSON_EXTRACT(f.calculation_json,'$.stock.lotCode') IS NOT NULL;
