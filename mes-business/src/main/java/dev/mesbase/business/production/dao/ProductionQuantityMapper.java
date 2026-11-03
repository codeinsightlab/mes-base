package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.ProductionReportModels.NodeTotal;
import dev.mesbase.business.production.domain.ProductionReportModels.Balance;
import dev.mesbase.business.production.domain.ProductionReportModels.ExecutionSummary;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import dev.mesbase.business.production.domain.ProductionInputBalance;
import dev.mesbase.business.production.domain.ProductionReportModels.ExecutionNodeTotal;
import dev.mesbase.business.production.domain.ProductionReportModels.ExecutionInputBalance;

/**
 * Only cross-table quantity projections; single-table persistence uses the generated mapper.
 */
public interface ProductionQuantityMapper {
    ProductionInputBalance inputBalance(@Param("executionCode") String executionCode);

    List<NodeTotal> nodeTotals(@Param("executionCode") String executionCode);

    List<ExecutionNodeTotal> nodeTotalsBatch(@Param("executionCodes") List<String> executionCodes);

    List<ExecutionInputBalance> inputBalances(@Param("executionCodes") List<String> executionCodes);

    Balance originalBalance(@Param("executionCode") String executionCode, @Param("reportId") Long reportId);

    List<ExecutionSummary> workorderQuantities(@Param("workorderId") Long workorderId);
}
