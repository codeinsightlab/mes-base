package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.ProductionReportModels.NodeTotal;
import dev.mesbase.business.production.domain.ProductionReportModels.Balance;
import dev.mesbase.business.production.domain.ProductionReportModels.ExecutionSummary;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import dev.mesbase.business.production.domain.ProductionInputBalance;

/**
 * Only cross-table quantity projections; single-table persistence uses the generated mapper.
 */
public interface ProductionQuantityMapper {
    ProductionInputBalance inputBalance(@Param("executionId") Long executionId);

    List<NodeTotal> nodeTotals(@Param("executionId") Long executionId);

    Balance originalBalance(@Param("executionId") Long executionId, @Param("reportId") Long reportId);

    List<ExecutionSummary> workorderQuantities(@Param("workorderId") Long workorderId);
}
