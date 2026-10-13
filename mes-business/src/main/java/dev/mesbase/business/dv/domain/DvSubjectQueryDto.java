// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.dv.domain;

import dev.mesbase.infrastructure.cache.BusinessCacheQuery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 设备点检保养项目分页查询条件。
 */
public record DvSubjectQueryDto(
        @Min(0) Integer offset,
        @Min(1) @Max(100) Integer limit,
        @Size(max=64) String factoryId,
        @Size(max=64) String subjectCode,
        @Size(max=255) String subjectName,
        @Size(max=64) String subjectType,
        @Size(max=500) String subjectContent,
        @Size(max=255) String subjectStandard,
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
        return "subject_id";
    }
}

