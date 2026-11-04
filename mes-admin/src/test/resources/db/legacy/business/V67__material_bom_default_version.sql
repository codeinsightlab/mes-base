-- A nullable generated key gives each product at most one marked BOM version.
-- NULL values for all non-default versions preserve the existing multi-version model.
ALTER TABLE material_bom
    ADD COLUMN is_default TINYINT NOT NULL DEFAULT 0 COMMENT '是否为所属产品的默认BOM版本',
    ADD COLUMN default_product_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN is_default = 1 THEN product_id ELSE NULL END
    ) STORED COMMENT '默认BOM产品唯一性约束生成列',
    ADD CONSTRAINT chk_material_bom_default_published
        CHECK (is_default IN (0, 1) AND (is_default = 0 OR status = 'PUBLISHED')),
    ADD UNIQUE KEY uk_material_bom_default_product (default_product_id);
