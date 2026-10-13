package dev.mesbase.business.production.service;

import dev.mesbase.business.production.domain.RoutingEdgeCommand;
import dev.mesbase.business.production.domain.RoutingNodeCommand;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * Static definition validation; edges alone decide topology, never display order.
 */
public final class RoutingGraphValidator {
    private RoutingGraphValidator() {
    }

    public static void validate(List<RoutingNodeCommand> nodes, List<RoutingEdgeCommand> edges,
                                boolean publishing) {
        if (nodes == null || edges == null || nodes.size() > 100 || edges.size() > 400) invalid();
        var incoming = new HashMap<String, Integer>();
        var outgoing = new HashMap<String, List<String>>();
        int keys = 0;
        for (var node : nodes) {
            if (node == null || node.nodeCode() == null || !node.nodeCode().matches("[A-Za-z0-9_-]{1,64}")
                    || node.operationId() == null || node.isKey() == null || node.displayOrder() == null
                    || node.displayOrder() < 0 || node.description() != null && node.description().length() > 1000
                    || incoming.putIfAbsent(node.nodeCode(), 0) != null) invalid();
            outgoing.put(node.nodeCode(), new ArrayList<>());
            if (node.isKey()) keys++;
        }
        if (keys > 1 || publishing && nodes.isEmpty()) invalid();
        var unique = new HashSet<RoutingEdgeCommand>();
        for (var edge : edges) {
            if (edge == null || edge.fromNodeCode() == null || edge.toNodeCode() == null
                    || !incoming.containsKey(edge.fromNodeCode()) || !incoming.containsKey(edge.toNodeCode())
                    || edge.fromNodeCode().equals(edge.toNodeCode()) || !unique.add(edge)) invalid();
            outgoing.get(edge.fromNodeCode()).add(edge.toNodeCode());
            incoming.compute(edge.toNodeCode(), (code, count) -> count + 1);
        }
        if (publishing && nodes.size() > 1 && incoming.keySet().stream()
                .anyMatch(code -> incoming.get(code) == 0 && outgoing.get(code).isEmpty())) invalid();
        var queue = new ArrayDeque<String>();
        incoming.forEach((code, count) -> {
            if (count == 0) queue.add(code);
        });
        int visited = 0;
        while (!queue.isEmpty()) {
            String code = queue.remove();
            visited++;
            for (String target : outgoing.get(code)) {
                int remaining = incoming.compute(target, (key, count) -> count - 1);
                if (remaining == 0) queue.add(target);
            }
        }
        if (visited != nodes.size()) invalid();
    }

    private static void invalid() {
        throw new FoundationException(ErrorCode.ROUTING_GRAPH_INVALID);
    }
}
