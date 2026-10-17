// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.wm.controller.vo;

import java.time.LocalDateTime;

/**
 * 成品仓储接收事实接口响应白名单。
 */
public record MesWarehouseAcceptanceVo(
        String id,
        String receiptId,
        String executionId,
        String productId,
        String stockId,
        String quantityDelta,
        String referenceId,
        String stockActionId,
        String requestHash,
        String operatorUserId,
        LocalDateTime recordedAt,
        String reason
) {
}

