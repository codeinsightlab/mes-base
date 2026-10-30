package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.ManufacturedInputSource;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * Cross-table receipt, production identity and current stock evidence.
 */
public interface ManufacturedInputSourceExtraMapper {
    List<ManufacturedInputSource> selectSources(@Param("bomId") Long bomId,
                                                @Param("materialId") Long materialId,
                                                @Param("unitId") Long unitId,
                                                @Param("warehouseIds") List<Long> warehouseIds);
}
