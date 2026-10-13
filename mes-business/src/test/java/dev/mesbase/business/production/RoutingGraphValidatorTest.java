package dev.mesbase.business.production;

import dev.mesbase.business.production.domain.RoutingEdgeCommand;
import dev.mesbase.business.production.domain.RoutingNodeCommand;
import dev.mesbase.business.production.service.RoutingGraphValidator;
import dev.mesbase.core.error.FoundationException;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoutingGraphValidatorTest {
    private RoutingNodeCommand node(String code) {
        return new RoutingNodeCommand(code, 1L, false, null, 0);
    }

    private RoutingEdgeCommand edge(String from, String to) {
        return new RoutingEdgeCommand(from, to);
    }

    @Test
    void linearBranchMergeAndRepeatedOperationsAreLegalRegardlessOfDisplayOrder() {
        var nodes = List.of(node("10"), node("20"), node("30A"), node("30B"), node("40"));
        assertDoesNotThrow(() -> RoutingGraphValidator.validate(nodes, List.of(edge("10", "20"),
                edge("20", "30A"), edge("20", "30B"), edge("30A", "40"), edge("30B", "40")), true));
        assertDoesNotThrow(() -> RoutingGraphValidator.validate(nodes.subList(0, 4), List.of(edge("10", "20"),
                edge("20", "30A"), edge("20", "30B")), true));
        assertDoesNotThrow(() -> RoutingGraphValidator.validate(nodes.subList(0, 3), List.of(edge("10", "20"), edge("20", "30A")), true));
    }

    @Test
    void rejectsCycleSelfLoopDuplicateAndUnknownEndpoints() {
        var nodes = List.of(node("a"), node("b"));
        for (var edges : List.of(List.of(edge("a", "b"), edge("b", "a")), List.of(edge("a", "a")),
                List.of(edge("a", "b"), edge("a", "b")), List.of(edge("a", "foreign")))) {
            assertThrows(FoundationException.class, () -> RoutingGraphValidator.validate(nodes, edges, false));
        }
    }

    @Test
    void allowsMultipleEntrancesAndExitsButRejectsIsolatedPublishedNodes() {
        var nodes = List.of(node("a"), node("b"), node("c"), node("d"));
        assertDoesNotThrow(() -> RoutingGraphValidator.validate(nodes, List.of(edge("a", "c"), edge("b", "c"), edge("c", "d")), true));
        assertDoesNotThrow(() -> RoutingGraphValidator.validate(nodes, List.of(), false));
        assertThrows(FoundationException.class, () -> RoutingGraphValidator.validate(nodes, List.of(edge("a", "b")), true));
        assertDoesNotThrow(() -> RoutingGraphValidator.validate(List.of(node("single")), List.of(), true));
        assertThrows(FoundationException.class, () -> RoutingGraphValidator.validate(List.of(node("a"), node("a")), List.of(), false));
    }
}
