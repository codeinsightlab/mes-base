package dev.mesbase.business.legacy;

import dev.mesbase.business.production.domain.ProductionReportModels.ExecutionQuantity;
import dev.mesbase.business.production.domain.ProductionReportModels.NodeQuantity;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.production.service.ProductionChainLocks;
import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.core.error.FoundationException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LegacyHistoryQuantityTest {
    private static BigDecimal q(String value) {
        return new BigDecimal(value);
    }

    private static NodeQuantity node(long id, String gross, String defect) {
        return new NodeQuantity(1L, id, id, "N" + id, "historical", (int) id, BigDecimal.ZERO, q(gross), q(defect), BigDecimal.ZERO, BigDecimal.ZERO, true);
    }

    @Test
    void usesExistingAuthorityForGross400Defect4Net396AndNoWritableAllowance() {
        var input = new ExecutionQuantity(1L, 1L, "LEGACY", 1L, q("400"), "LOCKED", false, "历史只读",
                List.of(node(1, "400", "0"), node(2, "400", "4"), node(3, "396", "0")), "source", q("400"), BigDecimal.ZERO, BigDecimal.ZERO, true);
        var result = ProductionQuantityService.project(input);
        assertEquals(q("396"), result.nodes().get(1).effectiveOutputQty());
        assertEquals(q("396"), result.nodes().get(2).inputQty());
        assertEquals(BigDecimal.ZERO, result.nodes().get(2).availableQty());
        assertFalse(result.writable());
        assertThrows(FoundationException.class, () -> ProductionQuantityService.validateChange(result, 2L, BigDecimal.ONE));
    }

    @Test
    void sharedMutationBoundaryRejectsHistoryButPreservesReleasedLocal() {
        var order = new WorkorderEntity();
        order.setStatus("HISTORICAL");
        assertThrows(FoundationException.class, () -> ProductionChainLocks.requireActive(order));
        order.setStatus("RELEASED");
        ProductionChainLocks.requireActive(order);
    }
}
