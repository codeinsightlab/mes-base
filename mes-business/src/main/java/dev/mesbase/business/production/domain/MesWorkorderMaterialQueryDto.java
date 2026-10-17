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
 * 本地工单用料需求分页查询条件。
 */
public record MesWorkorderMaterialQueryDto(
        @Min(0) Integer offset,
        @Min(1) @Max(100) Integer limit,
        Long materialId,
        Long unitId,
        @Digits(integer = 14, fraction = 6) BigDecimal totalRequiredQty,
        @Digits(integer = 14, fraction = 6) BigDecimal plannedQty,
        @Size(max = 2000) String warehouseIds,
        @Size(max = 100) String workCenter,
        Long revision,
        @Size(max = 16) String status,
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
        return "id";
    }
}

