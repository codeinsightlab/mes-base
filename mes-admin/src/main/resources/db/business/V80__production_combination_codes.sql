-- 组合码仅保存生产规划身份；不预占库存，不创建执行，不重放存量生产事实。
CREATE TABLE mes_production_combination (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '稳定组合二维码记录主键',
    workorder_id BIGINT NOT NULL COMMENT '所属生产工单主键',
    product_id BIGINT NOT NULL COMMENT '规划产出物料主键',
    output_unit_id BIGINT NOT NULL COMMENT '产出基本计量单位快照',
    demand_revision BIGINT NOT NULL COMMENT '生成时采用的工单材料需求修订号',
    planned_qty DECIMAL(20,6) NOT NULL COMMENT '需求比例对应的工单计划产出量快照，不是打印输入量',
    fingerprint CHAR(64) NOT NULL COMMENT '规范化需求行及原料批次集合的SHA256指纹，不含数量或库存位置',
    execution_id BIGINT NULL COMMENT '首次MES领料成功后绑定的唯一生产执行主键',
    created_by BIGINT NOT NULL COMMENT '生成组合的系统用户主键',
    created_at DATETIME(6) NOT NULL COMMENT '组合首次生成时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_production_combination_identity (workorder_id, demand_revision, fingerprint),
    KEY idx_production_combination_execution (execution_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生产组合二维码身份，不保存库存承诺或打印计划份额';

CREATE TABLE mes_production_combination_item (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '组合中需求行与原料批次的关联主键',
    combination_id BIGINT NOT NULL COMMENT '所属组合二维码记录主键',
    demand_line_id BIGINT NOT NULL COMMENT '来源工单实际用料需求行主键',
    line_no INT NOT NULL COMMENT '材料在工单BOM需求中的稳定顺序',
    material_id BIGINT NOT NULL COMMENT '当前需求材料主键',
    material_code VARCHAR(64) NOT NULL COMMENT '生成时需求材料品号快照',
    unit_id BIGINT NOT NULL COMMENT '材料基本计量单位快照',
    required_qty DECIMAL(20,6) NOT NULL COMMENT '该需求行的工单实际总需求量快照，多批次行不重复累加',
    batch_no VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT '固定原料批次原文，空值表示原库存无批次，不保存库存位置',
    PRIMARY KEY (id),
    KEY idx_production_combination_item_parent (combination_id, line_no, id),
    KEY idx_production_combination_item_demand (demand_line_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='按需求行顺序保存固定原料批次集合，不绑定仓库库位';

ALTER TABLE wm_stock_document_line
    ADD COLUMN demand_line_id BIGINT NULL COMMENT 'MES入口注入的工单用料需求行主键，通用仓储单据为空',
    ADD INDEX idx_stock_document_line_demand (demand_line_id);
