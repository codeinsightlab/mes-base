// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.pro.domain;

import dev.mesbase.infrastructure.cache.BusinessCacheQuery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 生产工单分页查询条件。
 */
public record ProWorkorderQueryDto(
        @Min(0) Integer offset,
        @Min(1) @Max(100) Integer limit,
        @Size(max=64) String workorderCode,
        @Size(max=255) String workorderName,
        @Size(max=64) String orderSource,
        @Size(max=64) String sourceCode,
        Long productId,
        @Size(max=64) String productCode,
        @Size(max=255) String productName,
        @Size(max=255) String productSpc,
        @Size(max=64) String unitOfMeasure,
        Double quantity,
        Double quantityProduced,
        Double quantityChanged,
        Double quantityScheduled,
        Long clientId,
        @Size(max=64) String clientCode,
        @Size(max=255) String clientName,
        @Size(max=64) String batchCode,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime requestDate,
        Long parentId,
        @Size(max=500) String ancestors,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime finishDate,
        @Size(max=64) String status,
        @Size(max=500) String remark,
        @Size(max=64) String attr1,
        @Size(max=255) String attr2,
        Integer attr3,
        Integer attr4,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime transactionDate,
        @Size(max=39) String moId
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
        return "workorder_id";
    }
}

