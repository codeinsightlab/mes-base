package dev.mesbase.business.quality.service;

import dev.mesbase.business.quality.domain.ProductionDefectModels.NodeTotal;
import java.util.List;
import java.util.Map;

/**
 * One current-business-database query, latest non-VOID contribution by affected node.
 */
public interface ProductionDefectQuantityQuery {
    List<NodeTotal> nodeTotals(Long executionId);

    Map<Long, List<NodeTotal>> nodeTotalsBatch(List<Long> executionIds);
}
