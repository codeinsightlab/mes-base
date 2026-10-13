package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.RoutingEntity;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * Cross-table product applicability query only; no single-table CRUD.
 */
public interface WorkOrderExtraMapper {
    List<RoutingEntity> publishedForProduct(@Param("productId") Long productId,
                                            @Param("search") String search);
}
