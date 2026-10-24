package dev.mesbase.business.production.controller.vo;


public record RoutingProductVo(
        String id, String materialId, String materialCode, String materialName,
        String currentMaterialCode, String currentMaterialName
) {
}
