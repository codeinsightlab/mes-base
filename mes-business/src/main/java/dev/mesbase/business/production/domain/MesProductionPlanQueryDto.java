// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.domain;

import dev.mesbase.infrastructure.cache.BusinessCacheQuery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 生产计划分页查询条件。
 */
public record MesProductionPlanQueryDto(
        @Min(0) Integer offset,
        @Min(1) @Max(100) Integer limit,
        @Size(max=64) String planNo,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate planDate,
        @Size(max=16) String status,
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

