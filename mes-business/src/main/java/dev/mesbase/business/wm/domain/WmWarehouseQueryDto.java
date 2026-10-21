package dev.mesbase.business.wm.domain;

import dev.mesbase.infrastructure.cache.BusinessCacheQuery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 仓库管理分页查询条件。
 */
public record WmWarehouseQueryDto(
        @Min(0) Integer offset,
        @Min(1) @Max(100) Integer limit,
        @Size(max = 64) String code,
        @Size(max = 255) String name,
        @Size(max = 16) String status,
        @Size(max = 500) String remark,
        @Size(max = 16) String source
) implements BusinessCacheQuery {
    public WmWarehouseQueryDto(Integer offset, Integer limit, String code, String name, String status, String remark) {
        this(offset, limit, code, name, status, remark, null);
    }

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

