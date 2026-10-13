package dev.mesbase.business.production.domain;

import java.util.List;

public record RoutingGraph(List<RoutingNodeCommand> nodes, List<RoutingEdgeCommand> edges) {
}
