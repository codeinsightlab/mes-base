package dev.mesbase.business.production;

import dev.mesbase.business.production.domain.BatchPathPolicy;
import dev.mesbase.business.production.domain.RoutingEdgeEntity;
import dev.mesbase.business.production.domain.RoutingNodeEntity;
import dev.mesbase.business.production.domain.WorkOrderModels.BatchInput;
import dev.mesbase.core.error.FoundationException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BatchPathPolicyTest {
    @Test
    void verifiesRealEdgesRatherThanDisplayOrder() {
        var nodes = List.of(1L, 2L, 3L, 4L, 5L).stream().map(id -> {
            var n = new RoutingNodeEntity();
            n.setId(id);
            n.setDisplayOrder(9 - id.intValue());
            return n;
        }).toList();
        var edges = List.of(edge(1, 2), edge(2, 3), edge(2, 4), edge(3, 5), edge(4, 5));
        assertDoesNotThrow(() -> BatchPathPolicy.validate(List.of(1L, 2L, 3L, 5L), nodes, edges));
        assertDoesNotThrow(() -> BatchPathPolicy.validate(List.of(1L, 2L, 4L, 5L), nodes, edges));
        for (var path : List.of(List.of(1L, 3L, 5L), List.of(1L, 2L), List.of(2L, 3L, 5L), List.of(1L, 2L, 2L, 5L), List.of(1L, 2L, 99L, 5L)))
            assertThrows(FoundationException.class, () -> BatchPathPolicy.validate(path, nodes, edges));
    }

    @Test
    void exactAllocationAndBatchIdentity() {
        assertDoesNotThrow(() -> BatchPathPolicy.allocation(new BigDecimal("0.3"), List.of(batch("19001", "0.1"), batch("19002", "0.2"))));
        assertThrows(FoundationException.class, () -> BatchPathPolicy.allocation(BigDecimal.ONE, List.of(batch("19001", "0.1"))));
        assertThrows(FoundationException.class, () -> BatchPathPolicy.allocation(BigDecimal.ONE, List.of(batch("19001", "0.5"), batch("19001", "0.5"))));
        assertThrows(FoundationException.class, () -> BatchPathPolicy.quantity(new BigDecimal("0.0000001")));
        assertThrows(FoundationException.class, () -> BatchPathPolicy.quantity(BigDecimal.ZERO));
    }

    private static BatchInput batch(String code, String qty) {
        return new BatchInput(null, code, new BigDecimal(qty), null);
    }

    private static RoutingEdgeEntity edge(long from, long to) {
        var e = new RoutingEdgeEntity();
        e.setFromNodeId(from);
        e.setToNodeId(to);
        return e;
    }
}
