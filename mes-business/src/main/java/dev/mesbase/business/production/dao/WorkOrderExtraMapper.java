package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.RoutingEntity;
import dev.mesbase.business.production.domain.WorkOrderModels;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * Cross-table product applicability and WorkOrder/Execution allocation; no single-table CRUD.
 */
public interface WorkOrderExtraMapper {
    long countBlockingIssues(Long workorderId);

    long countUnaccountedInputs(@Param("id") Long workorderId);

    WorkOrderModels.CompletionState completionState(@Param("id") Long workorderId);

    List<Long> completionCandidates(@Param("after") Long after, @Param("limit") int limit);

    List<WorkOrderModels.Allocation> allocationForOrders(@Param("ids") List<Long> ids);

    List<RoutingEntity> publishedForProduct(@Param("productId") Long productId,
                                            @Param("search") String search);
}
