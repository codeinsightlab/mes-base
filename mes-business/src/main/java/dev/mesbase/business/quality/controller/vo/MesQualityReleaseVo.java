// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.quality.controller.vo;

import java.time.LocalDateTime;

/**
 * 正式质量放行事实接口响应白名单。
 */
public record MesQualityReleaseVo(
        String id,
        String executionId,
        String sequenceNo,
        String actionType,
        String quantity,
        String basisHash,
        String referenceId,
        String requestHash,
        String operatorUserId,
        LocalDateTime recordedAt,
        String reason
) {
}

