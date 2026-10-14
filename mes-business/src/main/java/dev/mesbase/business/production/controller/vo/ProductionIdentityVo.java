package dev.mesbase.business.production.controller.vo;

public record ProductionIdentityVo(String executionId, String executionNo, String qrCode,
                                   String workorderId, String workorderCode, String productId, String productCode,
                                   String productName, String routingId, String routingCode, Integer routingVersion,
                                   String status, String targetQty) {
}
