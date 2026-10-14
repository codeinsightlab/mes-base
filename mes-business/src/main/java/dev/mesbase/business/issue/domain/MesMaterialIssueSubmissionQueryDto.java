// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.issue.domain;

import dev.mesbase.infrastructure.cache.BusinessCacheQuery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 领料正式提交尝试及冻结载荷历史分页查询条件。
 */
public record MesMaterialIssueSubmissionQueryDto(
        @Min(0) Integer offset,
        @Min(1) @Max(100) Integer limit,
        Long issueId,
        Integer pathRevision,
        @Size(max = 16) String status,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdAt,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime finishedAt
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

