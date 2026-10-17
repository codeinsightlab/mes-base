// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.wm.domain;

import dev.mesbase.infrastructure.cache.BusinessCacheQuery;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 本地仓储不可变收发存动作事实分页查询条件。
 */
public record WmStockActionQueryDto(
        @Min(0) Integer offset,
        @Min(1) @Max(100) Integer limit,
        @Size(max=64) String requestHash,
        Long stockId,
        Long materialId,
        Long unitId,
        Long warehouseId,
        @Size(max=128) String batchNo,
        @Size(max=64) String binCode,
        @Size(max=24) String actionType,
        @Digits(integer=14,fraction=6) BigDecimal quantityDelta,
        @Digits(integer=14,fraction=6) BigDecimal availableDelta,
        @Digits(integer=14,fraction=6) BigDecimal quantityBefore,
        @Digits(integer=14,fraction=6) BigDecimal quantityAfter,
        @Digits(integer=14,fraction=6) BigDecimal availableBefore,
        @Digits(integer=14,fraction=6) BigDecimal availableAfter,
        @Size(max=24) String sourceType,
        @Size(max=64) String sourceId,
        @Size(max=36) String referenceActionId,
        Long executionId,
        Long operatorUserId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime occurredAt,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime recordedAt,
        @Size(max=500) String reason
) implements BusinessCacheQuery {
    @Override
    public int cacheOffset() {
        return offset == null ? 0 : offset;
    }

    @Override
    public int cacheLimit() {
        return limit == null ? 20 : limit;
    }

    @Override
    public String cacheSort() {
        return "id";
    }
}

