package dev.mesbase.business.production;

import dev.mesbase.business.production.domain.ProductionReportModels.ExecutionQuantity;
import dev.mesbase.business.production.domain.ProductionReportModels.NodeQuantity;
import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.core.error.FoundationException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UnassessedDefectQuotaTest {
    static BigDecimal q(int n) { return BigDecimal.valueOf(n); }
    static ExecutionQuantity view(int independent, int unassessed) {
        var first = new NodeQuantity("E", 11L, 1L, "A", "A", 1, q(0), q(100),
                q(independent + unassessed), q(0), q(0), true, q(0), q(0), q(0), q(0),
                q(unassessed), q(independent));
        var second = new NodeQuantity("E", 12L, 2L, "B", "B", 2, q(0), q(0), q(0), q(0), q(0), true);
        return new ExecutionQuantity("E", 1L, 1L, q(100), "LOCKED", true, null,
                List.of(first, second), null, q(100), q(100), q(0), 1L);
    }
    @Test void unassessedDeclarationsReserveFlowAndAllowOnlyTheRemainingQuantity() {
        var result = ProductionQuantityService.project(view(0, 10));
        assertEquals(q(90), result.nodes().getFirst().effectiveOutputQty());
        assertEquals(q(90), result.nodes().getLast().inputQty());
        assertNull(ProductionQuantityService.positiveReportBlockReason(result, 12L));
        assertDoesNotThrow(() -> ProductionQuantityService.validateChange(result, 12L, q(90)));
        assertThrows(FoundationException.class, () -> ProductionQuantityService.validateChange(result, 12L, q(91)));
    }
    @Test void assessmentDoesNotChangeOccupiedQuantityAndDistinctDeclarationsAccumulate() {
        for (var pair : List.of(new int[]{0,10}, new int[]{10,0}, new int[]{5,5}))
            assertEquals(q(90), ProductionQuantityService.project(view(pair[0],pair[1])).nodes().getLast().inputQty());
        assertEquals(q(85), ProductionQuantityService.project(view(10,5)).nodes().getLast().inputQty());
        assertEquals(q(100), ProductionQuantityService.project(view(0,0)).nodes().getLast().inputQty());
    }
}
