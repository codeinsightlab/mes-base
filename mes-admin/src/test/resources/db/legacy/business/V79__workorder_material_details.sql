-- 工单级用料配置归属单头，原用料表扩展为一对多明细。
-- 原用料主键和数量保持不变；旧配置列保留迁移前快照，不再参与新业务计算。
ALTER TABLE workorder
    ADD COLUMN bom_id BIGINT NULL COMMENT '工单明确选择的BOM版本主键',
    ADD COLUMN bom_revision BIGINT NULL COMMENT '选料时核实的BOM内容修订号，旧记录未保存时为空',
    ADD COLUMN material_revision BIGINT NOT NULL DEFAULT 0 COMMENT '工单用料整体并发修订号',
    ADD COLUMN material_warehouse_ids VARCHAR(2000) NULL COMMENT '工单允许领料仓库主键的规范JSON数组',
    ADD COLUMN material_work_center VARCHAR(100) NULL COMMENT '工单本地生产工作中心说明',
    ADD COLUMN material_status VARCHAR(16) NULL COMMENT '工单用料配置启用状态，未配置时为空',
    ADD COLUMN material_frozen_at DATETIME(6) NULL COMMENT '首次正式领料固定整单用料依据的时间',
    ADD COLUMN material_updated_by BIGINT NULL COMMENT '最后维护工单用料的可信系统用户主键',
    ADD COLUMN material_updated_at DATETIME(6) NULL COMMENT '最后维护工单用料的时间',
    ADD INDEX idx_workorder_bom (bom_id);

UPDATE workorder w JOIN mes_workorder_material m ON m.id = w.id
SET w.bom_id = m.bom_id,
    w.material_revision = m.revision,
    w.material_warehouse_ids = m.warehouse_ids,
    w.material_work_center = m.work_center,
    w.material_status = m.status,
    w.material_frozen_at = m.frozen_at,
    w.material_updated_by = m.updated_by,
    w.material_updated_at = m.updated_at;

ALTER TABLE mes_workorder_material
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '工单用料明细独立主键，存量主键保持不变',
    ADD COLUMN workorder_id BIGINT NULL COMMENT '所属生产工单主键，由工单聚合保存固定注入',
    ADD COLUMN line_no INT NOT NULL DEFAULT 1 COMMENT '工单内明细展示顺序',
    ADD COLUMN bom_item_id BIGINT NULL COMMENT '选料来源的BOM直接组件行主键，人工新增或无法证明时为空',
    ADD COLUMN manually_adjusted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否人工调整或新增，产量变化时保留实际需求量',
    MODIFY COLUMN material_id BIGINT NOT NULL COMMENT '当前工单实际采用的组件物料主键',
    MODIFY COLUMN warehouse_ids VARCHAR(2000) NULL COMMENT '迁移前允许仓库快照，新业务以工单配置为准',
    MODIFY COLUMN work_center VARCHAR(100) NULL COMMENT '迁移前工作中心快照，新业务以工单配置为准',
    MODIFY COLUMN revision BIGINT NULL COMMENT '迁移前用料修订号快照，新业务以工单用料修订号为准',
    MODIFY COLUMN status VARCHAR(16) NULL COMMENT '迁移前用料状态快照，新业务以工单用料状态为准',
    MODIFY COLUMN bom_id BIGINT NULL COMMENT '迁移前BOM版本快照，新业务以工单BOM版本为准',
    MODIFY COLUMN frozen_at DATETIME(6) NULL COMMENT '迁移前固定时间快照，新业务以工单固定时间为准',
    ADD INDEX idx_workorder_material_order (workorder_id, line_no),
    ADD INDEX idx_workorder_material_bom_item (bom_item_id),
    COMMENT '工单直接材料需求明细，支持多组件和人工调整';

UPDATE mes_workorder_material SET workorder_id = id;
UPDATE mes_workorder_material m
JOIN (
    SELECT bom_id, material_id, unit_id, MIN(id) AS item_id
    FROM material_bom_item GROUP BY bom_id, material_id, unit_id HAVING COUNT(*) = 1
) b ON b.bom_id = m.bom_id AND b.material_id = m.material_id AND b.unit_id = m.unit_id
SET m.bom_item_id = b.item_id;

ALTER TABLE mes_workorder_material
    MODIFY COLUMN workorder_id BIGINT NOT NULL COMMENT '所属生产工单主键，由工单聚合保存固定注入';
