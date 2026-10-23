package dev.mesbase.business.basic.domain;

import java.math.BigDecimal;

/**
 * 本页组件事实及批量关联取得的当前物料、单位名称；不是历史快照。
 */
public record MaterialBomItemRow(Long id, Long bomId, Long materialId, BigDecimal quantity,
                                 Long unitId, Long childBomId, Integer sort, String materialCode, String materialName,
                                 String specification, String unitName) {
    public static MaterialBomItemRow of(MaterialBomItemEntity row, MaterialEntity material, UnitMeasureEntity unit) {
        if (material == null || unit == null) {
            return new MaterialBomItemRow(row.getId(), row.getBomId(), row.getMaterialId(), row.getQuantity(),
                    row.getUnitId(), row.getChildBomId(), row.getSort(), null, null, null, null);
        }
        return new MaterialBomItemRow(row.getId(), row.getBomId(), row.getMaterialId(), row.getQuantity(),
                row.getUnitId(), row.getChildBomId(), row.getSort(), material.getCode(), material.getName(),
                material.getSpecification(), unit.getName());
    }
}
