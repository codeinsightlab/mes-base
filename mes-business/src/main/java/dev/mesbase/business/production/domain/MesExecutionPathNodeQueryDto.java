package dev.mesbase.business.production.domain;

import dev.mesbase.infrastructure.cache.BusinessCacheQuery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 生产执行选定路径的有序节点引用分页查询条件。
 */
public record MesExecutionPathNodeQueryDto(
        @Min(0) Integer offset,
        @Min(1) @Max(100) Integer limit,
        Long executionId,
        Integer sequenceNo,
        Long routingNodeId
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

