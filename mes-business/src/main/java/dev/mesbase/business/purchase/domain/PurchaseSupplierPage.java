// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.purchase.domain;

import dev.mesbase.business.purchase.domain.PurchaseSupplierEntity;
import dev.mesbase.common.core.page.PageData;
import java.util.List;

/**
 * 供应商查询结果，不包含 HTTP 展示转换。
 */
public record PurchaseSupplierPage(
        List<PurchaseSupplierEntity> items,
        long total,
        int offset,
        int limit
) implements PageData<PurchaseSupplierEntity> {
}
