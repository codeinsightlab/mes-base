package dev.mesbase.business.wm.domain;

import dev.mesbase.infrastructure.cache.BusinessCacheQuery;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 库存管理分页查询条件。
 */
public record WmInventoryQueryDto(
        @Min(0) Integer offset,
        @Min(1) @Max(100) Integer limit,
        Long materialId,
        Long warehouseId,
        @Digits(integer = 14, fraction = 4) BigDecimal quantity,
        @Digits(integer = 14, fraction = 4) BigDecimal availableQuantity,
        @Size(max = 16) String status,
        @Size(max = 500) String remark
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

