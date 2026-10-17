// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.domain;

import dev.mesbase.infrastructure.cache.BusinessCacheQuery;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 正式生产完工事实分页查询条件。
 */
public record MesExecutionCompletionQueryDto(
        @Min(0) Integer offset,
        @Min(1) @Max(100) Integer limit,
        Long executionId,
        @Digits(integer = 14, fraction = 6) BigDecimal effectiveQty,
        @Digits(integer = 14, fraction = 6) BigDecimal releasedQty,
        @Digits(integer = 14, fraction = 6) BigDecimal receiptQty,
        @Digits(integer = 14, fraction = 6) BigDecimal acceptedQty,
        @Size(max = 64) String basisHash,
        @Size(max = 64) String requestHash,
        Long operatorUserId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime recordedAt,
        @Size(max = 500) String reason
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

