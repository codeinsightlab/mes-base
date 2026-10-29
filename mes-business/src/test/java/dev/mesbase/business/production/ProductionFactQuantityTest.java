package dev.mesbase.business.production;

import dev.mesbase.business.production.domain.ProductionReportModels.ExecutionQuantity;
import dev.mesbase.business.production.domain.ProductionReportModels.NodeQuantity;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.production.domain.ProductionCompletionBalance;
import dev.mesbase.business.production.service.ProductionChainLocks;
import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.core.error.FoundationException;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProductionFactQuantityTest {
    private static BigDecimal q(String value) {
        return new BigDecimal(value);
    }

    private static NodeQuantity node(long id, String gross, String defect) {
        return new NodeQuantity(1L, id, id, "N" + id, "formal", (int) id, BigDecimal.ZERO, q(gross), q(defect), BigDecimal.ZERO, BigDecimal.ZERO, true);
    }

    @Test
    void closedExecutionKeepsGross400Defect4Net396AndNoWritableAllowance() {
        var input = new ExecutionQuantity(1L, 1L, 1L, q("400"), "LOCKED", false, "执行已完全入库",
                List.of(node(1, "400", "0"), node(2, "400", "4"), node(3, "396", "0")), "source", q("400"), BigDecimal.ZERO, BigDecimal.ZERO, 1L);
        var result = ProductionQuantityService.project(input);
        assertEquals(q("396"), result.nodes().get(1).effectiveOutputQty());
        assertEquals(q("396"), result.nodes().get(2).inputQty());
        assertEquals(BigDecimal.ZERO, result.nodes().get(2).availableQty());
        assertFalse(result.writable());
        assertThrows(FoundationException.class, () -> ProductionQuantityService.validateChange(result, 2L, BigDecimal.ONE));
    }

    @Test
    void sharedMutationBoundaryRequiresCurrentBusinessStatesForEveryOrigin() {
        var order = new WorkorderEntity();
        for (String state : List.of("HISTORICAL", "UNRECOGNIZED", "COMPLETED", "CANCELLED")) {
            order.setStatus(state);
            assertThrows(FoundationException.class, () -> ProductionChainLocks.requireActive(order));
        }
        for (String state : List.of("DRAFT", "RELEASED")) {
            order.setStatus(state);
            assertDoesNotThrow(() -> ProductionChainLocks.requireActive(order));
        }
    }

    @Test
    void formalInputPreservesAllowanceAndAuditedCorrection() {
        var view = new ExecutionQuantity(1L, 1L, 1L, q("100"), "LOCKED", true, null,
                List.of(node(1, "40", "0")), "source", q("100"), q("100"), BigDecimal.ZERO, 1L);
        var projected = ProductionQuantityService.project(view);
        assertEquals(q("60"), projected.nodes().getFirst().availableQty());
        assertNull(ProductionQuantityService.positiveReportBlockReason(projected, 1L));
        assertDoesNotThrow(() -> ProductionQuantityService.validateChange(projected, 1L, q("-1")));
        assertThrows(FoundationException.class, () -> ProductionQuantityService.validateChange(projected, 1L, q("61")));

        var balanced = new ExecutionQuantity(1L, 1L, 1L, q("100"), "LOCKED", false, null,
                List.of(node(1, "100", "0")), "source", q("100"), q("100"), BigDecimal.ZERO, 1L);
        assertNull(ProductionCompletionBalance.blockedReason(ProductionQuantityService.project(balanced)));
    }
}
