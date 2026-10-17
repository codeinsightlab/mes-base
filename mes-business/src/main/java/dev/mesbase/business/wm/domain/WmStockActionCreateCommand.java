// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.wm.domain;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 本地仓储不可变收发存动作事实写入参数，字段来自已审查的表结构。
 */
public record WmStockActionCreateCommand(
        @NotBlank @Size(max=36) String id,
        @NotBlank @Size(max=64) String requestHash,
        @NotNull Long stockId,
        @NotNull Long materialId,
        @NotNull Long unitId,
        @NotNull Long warehouseId,
        @Size(max=128) String batchNo,
        @Size(max=64) String binCode,
        @NotBlank @Size(max=24) String actionType,
        @NotNull @Digits(integer=14,fraction=6) BigDecimal quantityDelta,
        @NotNull @Digits(integer=14,fraction=6) BigDecimal availableDelta,
        @NotNull @Digits(integer=14,fraction=6) BigDecimal quantityBefore,
        @NotNull @Digits(integer=14,fraction=6) BigDecimal quantityAfter,
        @NotNull @Digits(integer=14,fraction=6) BigDecimal availableBefore,
        @NotNull @Digits(integer=14,fraction=6) BigDecimal availableAfter,
        @NotBlank @Size(max=24) String sourceType,
        @Size(max=64) String sourceId,
        @Size(max=36) String referenceActionId,
        Long executionId,
        @NotNull Long operatorUserId,
        @NotNull LocalDateTime occurredAt,
        @NotNull LocalDateTime recordedAt,
        @NotBlank @Size(max=500) String reason
) {
}

