// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.controller.vo;

import java.time.LocalDateTime;

/**
 * 正式生产完工事实接口响应白名单。
 */
public record MesExecutionCompletionVo(
        String id,
        String executionId,
        String effectiveQty,
        String releasedQty,
        String receiptQty,
        String acceptedQty,
        String basisHash,
        String requestHash,
        String operatorUserId,
        LocalDateTime recordedAt,
        String reason
) {
}

