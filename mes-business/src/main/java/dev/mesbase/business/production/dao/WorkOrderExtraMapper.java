package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.RoutingEntity;
import dev.mesbase.business.production.domain.WorkOrderModels;
import java.util.List;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Param;

/**
 * Cross-table product applicability and WorkOrder/Execution allocation; no single-table CRUD.
 */
public interface WorkOrderExtraMapper {
    boolean hasOrderDependents(@Param("id") Long workorderId);

    boolean hasExecutionDependents(@Param("id") Long executionId);

    WorkOrderModels.Allocation activeAllocation(@Param("id") Long workorderId, @Param("excluding") Long excluding);

    BigDecimal pendingExecutionInput(@Param("id") Long executionId);

    long countBlockingIssues(Long workorderId);

    long countUnaccountedInputs(@Param("id") Long workorderId);

    WorkOrderModels.CompletionState completionState(@Param("id") Long workorderId);

    List<Long> executionCompletionCandidates(@Param("after") Long after, @Param("limit") int limit);

    List<Long> completionCandidates(@Param("after") Long after, @Param("limit") int limit);

    List<WorkOrderModels.Allocation> allocationForOrders(@Param("ids") List<Long> ids);

    List<RoutingEntity> publishedForProduct(@Param("productId") Long productId,
                                            @Param("search") String search);
}
