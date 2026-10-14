// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.domain;

import dev.mesbase.infrastructure.cache.BusinessCacheQuery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 生产执行当前完整路径分页查询条件。
 */
public record MesExecutionPathQueryDto(
        @Min(0) Integer offset,
        @Min(1) @Max(100) Integer limit,
        Long routingId,
        @Size(max=16) String pathStatus,
        Integer revision,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime selectedAt,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime lockedAt
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

