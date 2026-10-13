package dev.mesbase.business.production.controller.vo;

public record RoutingNodeVo(String id, String nodeCode, String operationId, Integer displayOrder,
                            String operationCode, String operationName, String operationDescription,
                            Boolean isKey, String description) {
}
