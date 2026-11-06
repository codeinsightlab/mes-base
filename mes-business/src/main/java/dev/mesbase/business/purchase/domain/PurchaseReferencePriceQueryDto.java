// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.purchase.domain;

import dev.mesbase.infrastructure.cache.BusinessCacheQuery;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 参考报价历史分页查询条件。
 */
public record PurchaseReferencePriceQueryDto(
        @Min(0) Integer offset,
        @Min(1) @Max(100) Integer limit,
        Long supplierId,
        Long materialId,
        Long unitId,
        @Digits(integer = 14, fraction = 6) BigDecimal unitPrice,
        @Size(max = 3) String currency,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveDate,
        @Size(max = 500) String remark
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
