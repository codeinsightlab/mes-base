-- 工单下达不冻结用料；首次正式领料成功时固定所选BOM比例。历史记录不补造依据。
ALTER TABLE mes_workorder_material
    ADD COLUMN bom_id BIGINT NULL COMMENT '明确选择的本地BOM版本主键',
    ADD COLUMN bom_base_qty DECIMAL(20,6) NULL COMMENT 'BOM基准产出数量，产出库存基本单位',
    ADD COLUMN bom_component_qty DECIMAL(20,6) NULL COMMENT '直接组件标准用量，材料库存基本单位',
    ADD COLUMN output_unit_id BIGINT NULL COMMENT 'BOM产出的库存基本单位主键',
    ADD COLUMN frozen_at DATETIME(6) NULL COMMENT '首次正式领料固定用料依据的时间',
    ADD INDEX idx_workorder_material_bom (bom_id);
