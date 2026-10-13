package dev.mesbase.business.production.controller.vo;

import java.util.List;

public record RoutingDetailVo(
        RoutingVo routing, List<RoutingProductVo> products, List<RoutingStepVo> steps
) {
}
