package dev.mesbase.business.basic.domain;

import dev.mesbase.infrastructure.cache.BusinessCacheQuery;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;

public record MaterialBomQueryDto(@Min(0) Integer offset, @Min(1) @Max(100) Integer limit, @Size(max = 64) String code,
                                  @Size(max = 32) String version, Long productId,
                                  @Size(max = 16) String status) implements BusinessCacheQuery {
    public int cacheOffset() {
        return offset == null ? 0 : offset;
    }

    public int cacheLimit() {
        return limit == null ? 20 : limit;
    }

    public String cacheSort() {
        return "id desc";
    }
}
