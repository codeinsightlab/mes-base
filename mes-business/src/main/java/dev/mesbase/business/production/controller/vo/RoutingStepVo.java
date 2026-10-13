// MES-CODEGEN: reviewed lifecycle extension of the generated scaffold.
package dev.mesbase.business.production.controller.vo;


public record RoutingStepVo(
        String id, String operationId, Integer sequence, String operationCode,
        String operationName, Boolean isKey, String description
) {
}
