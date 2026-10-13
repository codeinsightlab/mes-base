package dev.mesbase.business.production.controller.vo;


public record RoutingStepVo(
        String id, String operationId, Integer sequence, String operationCode,
        String operationName, String operationDescription, Boolean isKey, String description
) {
}
