package dev.mesbase.business.md.domain;

import dev.mesbase.infrastructure.cache.BusinessCacheQuery;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 车间分页查询条件。
 */
public record MdWorkshopQueryDto(
        @Min(0) Integer offset,
        @Min(1) @Max(100) Integer limit,

        @Size(max=64) String workshopCode,
        @Size(max=255) String workshopName,
        @Digits(integer=16,fraction=2) BigDecimal area,
        @Size(max=64) String charge,
        Long deptId,
        @Size(max=1) String enableFlag,
        @Size(max=500) String remark
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
        return "workshop_id";
    }
}

