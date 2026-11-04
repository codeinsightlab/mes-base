-- 修正旧批次及单材料语义，只调整元数据说明，不修改已有业务字段值。
ALTER TABLE workorder
    MODIFY COLUMN planned_qty DECIMAL(20,6) NOT NULL COMMENT '工单当前产出物料的计划数量，不分配原料批次或库存',
    COMMENT '单阶段生产工单单头，记录当前产出、工艺及用料依据';

ALTER TABLE mes_workorder_material
    MODIFY COLUMN total_required_qty DECIMAL(20,6) NOT NULL COMMENT '当前工单实际采用的材料需求量，允许人工调整',
    MODIFY COLUMN bom_component_qty DECIMAL(20,6) NULL COMMENT '所选BOM直接组件标准用量，采用来源组件基本单位';
