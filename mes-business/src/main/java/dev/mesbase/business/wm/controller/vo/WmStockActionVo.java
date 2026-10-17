package dev.mesbase.business.wm.controller.vo;

import java.time.LocalDateTime;

/**
 * 本地仓储不可变收发存动作事实接口响应白名单。
 */
public record WmStockActionVo(
        String id,
        String requestHash,
        String stockId,
        String materialId,
        String unitId,
        String warehouseId,
        String batchNo,
        String binCode,
        String actionType,
        String quantityDelta,
        String availableDelta,
        String quantityBefore,
        String quantityAfter,
        String availableBefore,
        String availableAfter,
        String sourceType,
        String sourceId,
        String referenceActionId,
        String executionId,
        String operatorUserId,
        LocalDateTime occurredAt,
        LocalDateTime recordedAt,
        String reason,
        String documentLineId,
        String operationId
) {
}

