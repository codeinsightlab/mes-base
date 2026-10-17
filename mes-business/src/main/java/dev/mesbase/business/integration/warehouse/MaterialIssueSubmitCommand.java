package dev.mesbase.business.integration.warehouse;

import java.math.BigDecimal;
import dev.mesbase.business.integration.production.ProductionSupply.Calculation;
import java.util.List;

/**
 * Immutable vendor-neutral snapshot. No credentials or vendor request DTOs.
 */
public record MaterialIssueSubmitCommand(Long issueId, String operationKey, String issueNo,
                                         Long workorderId, String workorderNo, String externalWorkorderId,
                                         String externalWorkorderCode,
                                         Long executionId, String executionNo, Long productId, String productCode,
                                         Long routingId,
                                         Integer pathRevision, List<Long> routingNodeIds, String issueTime,
                                         List<Line> lines, Input input, Source source) {
    public record Source(String provider, String bindingKey, Long factoryRevision, Long configurationRevision) {
    }

    public MaterialIssueSubmitCommand(Long issueId, String operationKey, String issueNo,
                                      Long workorderId, String workorderNo, String externalWorkorderId,
                                      String externalWorkorderCode,
                                      Long executionId, String executionNo, Long productId, String productCode,
                                      Long routingId,
                                      Integer pathRevision, List<Long> routingNodeIds, String issueTime,
                                      List<Line> lines, Input input) {
        this(issueId, operationKey, issueNo, workorderId, workorderNo, externalWorkorderId, externalWorkorderCode,
                executionId, executionNo, productId, productCode, routingId, pathRevision, routingNodeIds,
                issueTime, lines, input, null);
    }

    public MaterialIssueSubmitCommand {
        routingNodeIds = List.copyOf(routingNodeIds);
        lines = List.copyOf(lines);
    }

    public record Input(String requestId, String inputKind, BigDecimal outputQty, Calculation calculation) {
    }

    public record Line(Long itemId, Long materialId, String materialCode, String batchNo,
                       BigDecimal quantity, Long unitId, String unitCode, Long warehouseId, String warehouseCode,
                       String externalWarehouseId) {
        public Line(Long itemId, Long materialId, String materialCode, String batchNo, BigDecimal quantity,
                    Long unitId, String unitCode, Long warehouseId, String warehouseCode) {
            this(itemId, materialId, materialCode, batchNo, quantity, unitId, unitCode, warehouseId, warehouseCode, null);
        }
    }
}
