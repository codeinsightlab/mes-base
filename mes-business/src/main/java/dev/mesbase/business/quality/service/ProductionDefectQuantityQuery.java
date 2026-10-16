package dev.mesbase.business.quality.service;

import dev.mesbase.business.quality.domain.ProductionDefectModels.NodeTotal;
import java.util.List;

/**
 * One current-business-database query, latest non-VOID contribution by affected node.
 */
public interface ProductionDefectQuantityQuery {
    List<NodeTotal> nodeTotals(Long executionId);
}
