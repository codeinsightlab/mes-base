package dev.mesbase.business.production.controller.vo;


public record RoutingProductVo(
        String id, String materialId,
        String currentMaterialCode, String currentMaterialName
) {
}
