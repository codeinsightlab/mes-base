package dev.mesbase.business.production.domain;

import dev.mesbase.business.basic.domain.MaterialEntity;
import java.util.List;
import java.util.Map;

public record RoutingDetail(
        RoutingEntity routing,
        List<RoutingProductEntity> products,
        List<RoutingNodeEntity> nodes,
        List<RoutingEdgeEntity> edges,
        Map<Long, MaterialEntity> currentProducts
) {
}
