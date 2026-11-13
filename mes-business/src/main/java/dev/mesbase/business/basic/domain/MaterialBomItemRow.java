package dev.mesbase.business.basic.domain;

import java.util.Optional;
import java.math.BigDecimal;

/**
 * 本页组件事实及批量关联取得的当前物料、单位名称；不是历史快照。
 */
public record MaterialBomItemRow(Long id, Long bomId, Long materialId, BigDecimal quantity,
                                 Long unitId, Long childBomId, Integer sort, String materialCode, String materialName,
                                 String specification, String unitName, String childBomCode, String childBomVersion) {
    public static MaterialBomItemRow of(MaterialBomItemEntity row, MaterialEntity material, UnitMeasureEntity unit,
                                        MaterialBomEntity childBom) {
        String childCode = Optional.ofNullable(childBom).map(MaterialBomEntity::getCode).orElse(null);
        String childVersion = Optional.ofNullable(childBom).map(MaterialBomEntity::getVersion).orElse(null);
        if (material == null || unit == null) {
            return new MaterialBomItemRow(row.getId(), row.getBomId(), row.getMaterialId(), row.getQuantity(),
                    row.getUnitId(), row.getChildBomId(), row.getSort(), null, null, null, null, childCode, childVersion);
        }
        return new MaterialBomItemRow(row.getId(), row.getBomId(), row.getMaterialId(), row.getQuantity(),
                row.getUnitId(), row.getChildBomId(), row.getSort(), material.getCode(), material.getName(),
                material.getSpecification(), unit.getName(), childCode, childVersion);
    }
}
