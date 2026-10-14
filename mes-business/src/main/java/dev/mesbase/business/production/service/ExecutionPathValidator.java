package dev.mesbase.business.production.service;

import dev.mesbase.business.production.domain.RoutingNodeEntity;
import dev.mesbase.business.production.domain.RoutingEdgeEntity;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One complete root-to-sink path, by node identity, never by operation or display order.
 */
public final class ExecutionPathValidator {
    private ExecutionPathValidator() {
    }

    public static void validate(List<Long> path, List<RoutingNodeEntity> nodes, List<RoutingEdgeEntity> edges) {
        if (path == null || path.isEmpty() || path.size() > 100 || path.stream()
                .anyMatch(x -> x == null) || new HashSet<>(path).size() != path.size()) invalid();
        Set<Long> known = new HashSet<>();
        nodes.forEach(n -> known.add(n.getId()));
        if (!known.containsAll(path)) invalid();
        for (var n : nodes) if (Boolean.TRUE.equals(n.getIsKey()) && !path.contains(n.getId())) invalid();
        if (edges.stream().anyMatch(e -> e.getToNodeId().equals(path.getFirst())) || edges.stream()
                .anyMatch(e -> e.getFromNodeId().equals(path.getLast()))) invalid();
        for (int i = 1; i < path.size(); i++) {
            Long from = path.get(i - 1), to = path.get(i);
            if (edges.stream().noneMatch(e -> from.equals(e.getFromNodeId()) && to.equals(e.getToNodeId()))) invalid();
        }
    }

    private static void invalid() {
        throw new FoundationException(ErrorCode.ROUTING_GRAPH_INVALID);
    }
}
