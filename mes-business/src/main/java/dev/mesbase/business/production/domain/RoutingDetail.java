package dev.mesbase.business.production.domain;

import java.util.List;

public record RoutingDetail(
        RoutingEntity routing,
        List<RoutingProductEntity> products,
        List<RoutingNodeEntity> nodes,
        List<RoutingEdgeEntity> edges
) {
}
