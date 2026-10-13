package dev.mesbase.business.issue.domain;

import dev.mesbase.infrastructure.cache.BusinessCacheQuery;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 领料出库明细分页查询条件。
 */
public record MesMaterialIssueItemQueryDto(
        @Min(0) Integer offset,
        @Min(1) @Max(100) Integer limit,
        Long issueId,
        Long materialId,
        @Size(max = 64) String materialCode,
        @Size(max = 128) String batchNo,
        @Digits(integer = 14, fraction = 6) BigDecimal quantity,
        Long unitId,
        @Size(max = 64) String unitCode,
        Long warehouseId,
        @Size(max = 64) String warehouseCode,
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
        return "id";
    }
}

