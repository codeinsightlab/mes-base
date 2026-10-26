package dev.mesbase.business.production;

import dev.mesbase.business.production.domain.ProductionReportModels.NodeQuantity;
import dev.mesbase.business.production.domain.ProductionReportModels.ExecutionQuantity;
import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.core.error.FoundationException;

import java.math.BigDecimal;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionQualityPropagationTest {
    static BigDecimal d(int v) {
        return BigDecimal.valueOf(v);
    }

    static ExecutionQuantity project(int[] reported, int[] defects, int[] recovered) {
        var nodes = new ArrayList<NodeQuantity>();
        for (int i = 0; i < reported.length; i++)
            nodes.add(new NodeQuantity(1L, (long) i + 1, 1L, "N" + i, "TEST", i + 1, d(0), d(reported[i]), d(defects[i]), d(0), d(0), true, d(recovered[i]), d(0), d(0), d(0), d(0)));
        return ProductionQuantityService.project(new ExecutionQuantity(1L, 1L, 1L, d(1), "LOCKED", true, null, nodes, null, d(100), d(100), d(0), 1L));
    }

    static void eq(int v, BigDecimal got) {
        assertEquals(0, d(v).compareTo(got));
    }

    @Test
    void fiveNodeAcceptanceAndPartialProgressNeverManufactureReports() {
        var a = project(new int[]{100, 100, 100, 100, 100}, new int[]{0, 10, 0, 0, 0}, new int[5]);
        eq(90, a.nodes().getLast().effectiveOutputQty());
        for (int i = 2; i < 5; i++) {
            eq(10, a.nodes().get(i).historicalOverrunQty());
            eq(100, a.nodes().get(i).reportedQty());
            eq(0, a.nodes().get(i).defectQty());
        }
        var b = project(new int[]{100, 100, 100, 100, 100}, new int[]{0, 10, 0, 0, 0}, new int[]{0, 4, 0, 0, 0});
        eq(94, b.nodes().getLast().effectiveOutputQty());
        var c = project(new int[]{100, 100, 100, 80, 60}, new int[]{0, 10, 0, 0, 0}, new int[5]);
        eq(10, c.nodes().get(3).availableQty());
        eq(20, c.nodes().get(4).availableQty());
        eq(60, c.nodes().getLast().effectiveOutputQty());
        var cr = project(new int[]{100, 100, 100, 80, 60}, new int[]{0, 10, 0, 0, 0}, new int[]{0, 4, 0, 0, 0});
        eq(14, cr.nodes().get(3).availableQty());
        eq(60, cr.nodes().getLast().effectiveOutputQty());
        var multiple = project(new int[]{100, 100, 100, 100, 100}, new int[]{0, 10, 3, 0, 0}, new int[5]);
        eq(87, multiple.nodes().getLast().effectiveOutputQty());
        var partial = project(new int[]{100, 100, 100, 100, 100}, new int[]{0, 10, 0, 0, 0}, new int[]{0, 3, 0, 0, 0});
        eq(93, partial.nodes().getLast().effectiveOutputQty());
        assertThrows(FoundationException.class, () -> ProductionQuantityService.validateChange(a, 3L, d(1)));
    }

    @Test
    void clampsPreserveSignedRawConflictInsteadOfHidingContradiction() {
        var view = project(new int[]{100, 100, 100}, new int[]{100, 10, 0}, new int[3]);
        eq(-10, view.nodes().get(1).rawEffectiveOutputQty());
        eq(0, view.nodes().get(1).effectiveOutputQty());
        eq(-100, view.nodes().get(1).rawAvailableQty());
    }
}
