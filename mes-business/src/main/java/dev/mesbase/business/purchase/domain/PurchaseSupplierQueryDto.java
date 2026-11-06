// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.purchase.domain;

import dev.mesbase.infrastructure.cache.BusinessCacheQuery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 供应商分页查询条件。
 */
public record PurchaseSupplierQueryDto(
        @Min(0) Integer offset,
        @Min(1) @Max(100) Integer limit,
        @Size(max = 64) String code,
        @Size(max = 200) String name,
        @Size(max = 100) String contactPerson,
        @Size(max = 64) String contactPhone,
        @Size(max = 500) String address,
        @Size(max = 16) String status,
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
