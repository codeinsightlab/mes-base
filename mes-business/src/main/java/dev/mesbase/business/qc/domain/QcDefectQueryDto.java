// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.qc.domain;

import dev.mesbase.infrastructure.cache.BusinessCacheQuery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 质量缺陷分页查询条件。
 */
public record QcDefectQueryDto(
        @Min(0) Integer offset,
        @Min(1) @Max(100) Integer limit,
        @Size(max = 64) String factoryId,
        @Size(max = 64) String defectCode,
        @Size(max = 500) String defectName,
        @Size(max = 64) String indexType,
        @Size(max = 64) String defectLevel,
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
        return "defect_id";
    }
}

