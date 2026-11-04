-- Business purpose only; existing warehouses remain unclassified for explicit review.
-- No inventory, document, source or execution facts are changed.
ALTER TABLE wm_warehouse
    ADD COLUMN warehouse_type VARCHAR(32) NULL DEFAULT NULL COMMENT '仓库业务用途编码；NULL 表示待补录';
CREATE INDEX idx_wm_warehouse_type ON wm_warehouse (warehouse_type);
