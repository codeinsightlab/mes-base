// MES-CODEGEN: reviewed lifecycle extension of the generated scaffold.
package dev.mesbase.business.production.domain;

import java.util.List;

public record RoutingDetail(
        RoutingEntity routing,
        List<RoutingProductEntity> products,
        List<RoutingStepEntity> steps
) {
}
