package dev.mesbase.business.production.domain;

import dev.mesbase.infrastructure.cache.BusinessCacheQuery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 工艺路线分页查询条件。
 */
public record RoutingQueryDto(
        @Min(0) Integer offset,
        @Min(1) @Max(100) Integer limit,
        @Size(max = 64) String code,
        @Size(max = 255) String name,
        Integer version,
        @Size(max = 16) String status,
        @Size(max = 1000) String description,
        @Size(max = 128) String externalId,
        @Size(max = 128) String sourceVersion,
        Integer revision,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime publishedAt
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

