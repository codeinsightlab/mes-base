// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.sales.domain;

import dev.mesbase.infrastructure.cache.BusinessCacheQuery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 销售订单分页查询条件。
 */
public record MesSalesOrderQueryDto(
        @Min(0) Integer offset,
        @Min(1) @Max(100) Integer limit,
        @Size(max=64) String orderNo,
        Long customerId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate orderDate,
        @Size(max=16) String status,
        @Size(max=16) String sourceType,
        @Size(max=128) String externalId,
        @Size(max=500) String remark,
        Integer revision,
        Long createdBy,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdAt,
        Long updatedBy,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime updatedAt
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

