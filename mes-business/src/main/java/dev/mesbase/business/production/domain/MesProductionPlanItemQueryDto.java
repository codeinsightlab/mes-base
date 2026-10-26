// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.domain;

import dev.mesbase.infrastructure.cache.BusinessCacheQuery;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 生产计划明细分页查询条件。
 */
public record MesProductionPlanItemQueryDto(
        @Min(0) Integer offset,
        @Min(1) @Max(100) Integer limit,
        Long planId,
        Integer lineNo,
        Long materialId,
        @Digits(integer=14,fraction=6) BigDecimal plannedQty,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime plannedStartAt,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime plannedEndAt,
        Integer priority,
        @Size(max=500) String remark,
        Integer revision,
        Long createdBy,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdAt,
        Long updatedBy,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime updatedAt
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
        return "id desc";
    }
}

