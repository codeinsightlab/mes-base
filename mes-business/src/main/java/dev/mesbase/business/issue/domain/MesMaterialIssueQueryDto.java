package dev.mesbase.business.issue.domain;

import dev.mesbase.infrastructure.cache.BusinessCacheQuery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 领料出库单分页查询条件。
 */
public record MesMaterialIssueQueryDto(
        @Min(0) Integer offset,
        @Min(1) @Max(100) Integer limit,
        @Size(max = 64) String issueNo,
        Long workorderId,
        @Size(max = 64) String workorderCode,
        Long productId,
        @Size(max = 64) String productCode,
        @Size(max = 128) String flowCardNo,
        Long routingId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime issueTime,
        @Size(max = 16) String status,
        @Size(max = 500) String remark,
        Long executionId
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

