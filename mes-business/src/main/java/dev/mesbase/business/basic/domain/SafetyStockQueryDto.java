package dev.mesbase.business.basic.domain;

import dev.mesbase.infrastructure.cache.BusinessCacheQuery;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 安全库存配置分页查询条件。
 */
public record SafetyStockQueryDto(
        @Min(0) Integer offset,
        @Min(1) @Max(100) Integer limit,
        Long materialId,
        @Digits(integer = 14, fraction = 4) BigDecimal minQty,
        @Digits(integer = 14, fraction = 4) BigDecimal maxQty,
        @Size(max = 16) String status
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

