package dev.mesbase.business.basic.dao;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * Copies direct components in one database operation within the BOM transaction.
 */
public interface MaterialBomCopyMapper {
    @Insert("INSERT INTO material_bom_item (bom_id, material_id, quantity, unit_id, child_bom_id, sort) "
            + "SELECT #{targetId}, material_id, quantity, unit_id, child_bom_id, sort "
            + "FROM material_bom_item WHERE bom_id = #{sourceId}")
    int copyItems(@Param("sourceId") Long sourceId, @Param("targetId") Long targetId);
}
