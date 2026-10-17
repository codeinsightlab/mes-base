// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.quality.domain;

import dev.mesbase.infrastructure.cache.BusinessCacheQuery;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 正式质量放行事实分页查询条件。
 */
public record MesQualityReleaseQueryDto(
        @Min(0) Integer offset,
        @Min(1) @Max(100) Integer limit,
        Long executionId,
        Long sequenceNo,
        @Size(max = 16) String actionType,
        @Digits(integer = 14, fraction = 6) BigDecimal quantity,
        @Size(max = 64) String basisHash,
        @Size(max = 36) String referenceId,
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

