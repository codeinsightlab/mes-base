-- 源系统扣料记录使用六位小数；保持源材料数量精度，不将其截断为库存汇总精度。
ALTER TABLE mes_material_issue_item MODIFY quantity DECIMAL(20,6) NOT NULL
    COMMENT '本次领料数量，保留六位小数，不等于生产或报工数量';
