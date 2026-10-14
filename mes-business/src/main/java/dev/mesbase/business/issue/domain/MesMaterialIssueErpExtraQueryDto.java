package dev.mesbase.business.issue.domain;

import dev.mesbase.infrastructure.cache.BusinessCacheQuery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 领料ERP外部事实分页查询条件。
 */
public record MesMaterialIssueErpExtraQueryDto(
        @Min(0) Integer offset,
        @Min(1) @Max(100) Integer limit,
        @Size(max = 20) String sourceExecutionMode,
        @Size(max = 20) String externalEffect,
        @Size(max = 128) String externalBindingKey,
        @Size(max = 128) String externalReference,
        @Size(max = 20) String voidResultStatus,
        @Size(max = 20) String voidExecutionMode,
        @Size(max = 500) String voidMessage,
        @Size(max = 128) String provider,
        @Size(max = 128) String externalCode,
        @Size(max = 30) String documentStatus,
        @Size(max = 30) String lastOperation,
        @Size(max = 30) String lastOutcome,
        @Size(max = 500) String lastMessage,
        Long operationVersion,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime checkedAt
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

