// MES-CODEGEN: scaffold; review required.
package dev.mesbase.modules.material.domain;

import dev.mesbase.infrastructure.cache.BusinessCacheQuery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 物料产品分页查询条件。
 */
public record MaterialQueryDto(
        @Min(0) Integer offset,
        @Min(1) @Max(100) Integer limit,
        @Size(max = 64) String code,
        @Size(max = 255) String name,
        @Size(max = 500) String specification,
        Long categoryId,
        Long unitId,
        @Size(max = 16) String materialType,
        @Size(max = 16) String status,
        @Size(max = 128) String externalId,
        @Size(max = 128) String externalCode
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

