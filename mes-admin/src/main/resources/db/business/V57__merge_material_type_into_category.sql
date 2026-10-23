-- 物料标识并入统一分类：保留已有分类，未分类物料按原标识归入“材料/产品”。
-- 先检查旧值及分类编码冲突，再迁移；不修改物料身份、单位或任何生产/库存事实。
CREATE TEMPORARY TABLE material_category_merge_guard (
    valid TINYINT NOT NULL CHECK (valid = 1)
);
INSERT INTO material_category_merge_guard(valid)
SELECT IF(COUNT(*) = 0, 1, 0) FROM material
WHERE material_type IS NULL OR material_type NOT IN ('ITEM', 'PRODUCT');
INSERT INTO material_category_merge_guard(valid)
SELECT IF(COUNT(*) = 0, 1, 0) FROM material_category
WHERE (code = 'ITEM' AND (name <> '材料' OR parent_id IS NOT NULL OR status <> 'ENABLED'))
   OR (code = 'PRODUCT' AND (name <> '产品' OR parent_id IS NOT NULL OR status <> 'ENABLED'));
INSERT INTO material_category(code,name,parent_id,sort,status,remark)
SELECT 'ITEM','材料',NULL,0,'ENABLED','由物料标识合并'
WHERE NOT EXISTS (SELECT 1 FROM material_category WHERE code='ITEM');
INSERT INTO material_category(code,name,parent_id,sort,status,remark)
SELECT 'PRODUCT','产品',NULL,1,'ENABLED','由物料标识合并'
WHERE NOT EXISTS (SELECT 1 FROM material_category WHERE code='PRODUCT');
UPDATE material m JOIN material_category c ON c.code=m.material_type
SET m.category_id=c.id WHERE m.category_id IS NULL;
DROP TEMPORARY TABLE material_category_merge_guard;
ALTER TABLE material DROP COLUMN material_type;
