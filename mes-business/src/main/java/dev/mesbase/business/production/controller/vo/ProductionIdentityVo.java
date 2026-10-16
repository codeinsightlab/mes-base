package dev.mesbase.business.production.controller.vo;

public record ProductionIdentityVo(String executionId, String executionNo, String qrCode,
                                   String workorderId, String workorderCode, String productId, String productCode,
                                   String productName, String routingId, String routingCode, Integer routingVersion,
                                   String status, String targetQty, String identityType) {
    public ProductionIdentityVo(String executionId, String executionNo, String qrCode, String workorderId,
                                String workorderCode,
                                String productId, String productCode, String productName, String routingId,
                                String routingCode, Integer routingVersion, String status, String targetQty) {
        this(executionId, executionNo, qrCode, workorderId, workorderCode, productId, productCode, productName, routingId, routingCode, routingVersion, status, targetQty,
                qrCode.startsWith("MES1.") ? "SINGLE_MATERIAL_V1" : "EXECUTION");
    }
}
