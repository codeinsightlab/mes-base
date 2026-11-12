package dev.mesbase.business.production;

import dev.mesbase.business.production.domain.ProductionCombinationPlanner;
import dev.mesbase.business.production.domain.ProductionCompletionBalance;
import dev.mesbase.business.production.domain.ProductionReportModels.MaterialReturnBalance;
import dev.mesbase.business.production.domain.ProductionReportModels.ExecutionQuantity;
import dev.mesbase.business.production.domain.ProductionReportModels.NodeQuantity;
import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.core.error.FoundationException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProductionMaterialReturnQuantityTest {
    private static BigDecimal q(String value) {
        return new BigDecimal(value);
    }

    private static MaterialReturnBalance material(long demand, String gross, String returned, int precision) {
        return new MaterialReturnBalance("EX", null, null, null, demand, q(gross), q(returned), precision, 0, q(gross), q("10"), 1, 1);
    }

    @Test
    void materialUnitsAreConvertedAndTheOriginalRoundedAllocationRemainsValid() {
        assertEquals(0, q("9").compareTo(ProductionCombinationPlanner.retainedOutput(q("10"),
                List.of(material(1, "20", "2", 0)))));
        assertEquals(0, q("1").compareTo(ProductionCombinationPlanner.retainedOutput(q("1"),
                List.of(new MaterialReturnBalance("EX", null, null, null, 1L, q("0.333333"), q("0"), 0, 0, q("1"), q("3"), 1, 0)))));
        assertEquals(0, q("0.9").compareTo(ProductionCombinationPlanner.retainedOutput(q("1"),
                List.of(new MaterialReturnBalance("EX", null, null, null, 1L, q("2"), q("0.2"), 1, 0, q("2"), q("1"), 1, 1)))));
    }

    @Test
    void returnedSurplusAndSubsequentFormalIssuesAreNotMistakenForAChangedBomRatio() {
        var surplus = new MaterialReturnBalance("EX", null, null, null, 1L,
                q("42"), q("2"), 0, 0, q("20"), q("10"), 2, 1);
        assertEquals(0, q("20").compareTo(ProductionCombinationPlanner.retainedOutput(q("20"), List.of(surplus))));
        var rounded = new MaterialReturnBalance("EX", null, null, null, 1L,
                q("0.333334"), q("0.000001"), 0, 0, q("1"), q("3"), 1, 1);
        assertEquals(0, q("1").compareTo(ProductionCombinationPlanner.retainedOutput(q("1"), List.of(rounded))));
    }

    @Test
    void multipleMaterialsUseTheBottleneckWithoutAddingDifferentUnits() {
        assertEquals(0, q("8").compareTo(ProductionCombinationPlanner.retainedOutput(q("10"),
                List.of(material(1, "20", "2", 0), material(2, "30", "6", 0)))));
    }

    @Test
    void fullReturnHasZeroInputAndOverReturnIsRejected() {
        assertEquals(0, ProductionCombinationPlanner.retainedOutput(q("10"),
                List.of(material(1, "20", "20", 0))).signum());
        assertThrows(FoundationException.class, () -> ProductionCombinationPlanner.retainedOutput(q("10"),
                List.of(material(1, "20", "21", 0))));
    }

    @Test
    void completionBalancesTenAsOneReturnedOneDefectAndEightGoodWithoutRewritingTen() {
        var node = new NodeQuantity("EX", 11L, 1L, "N", "step", 1, q("0"), q("9"), q("1"),
                q("0"), q("0"), true);
        var view = new ExecutionQuantity("EX", 1L, 1L, q("10"), "LOCKED", true, null,
                List.of(node), null, q("9"), q("10"), q("0"), 1L);
        var result = ProductionQuantityService.project(view);
        assertEquals(q("10"), result.confirmedInputQty());
        assertEquals(q("1"), result.materialReturnQty());
        assertEquals(q("8"), result.nodes().getFirst().effectiveOutputQty());
        assertNull(ProductionCompletionBalance.blockedReason(result));
        assertThrows(FoundationException.class, () -> ProductionQuantityService.validateChange(result, 11L, q("1")));
    }

    @Test
    void zeroInputCannotBeDeclaredFullyReceivedEvenWhenTheEquationBalances() {
        var view = new ExecutionQuantity("EX", 1L, null, q("10"), null, false, null,
                List.of(), null, q("0"), q("10"), q("0"), 1L);
        assertNotNull(ProductionCompletionBalance.blockedReason(view));
    }
}
