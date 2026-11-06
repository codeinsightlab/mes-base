package dev.mesbase.business.purchase.domain;

import dev.mesbase.business.purchase.domain.PurchaseReferencePriceRow;
import dev.mesbase.common.core.page.PageData;
import java.util.List;

/**
 * 参考报价历史查询结果，不包含 HTTP 展示转换。
 */
public record PurchaseReferencePricePage(
        List<PurchaseReferencePriceRow> items,
        long total,
        int offset,
        int limit
) implements PageData<PurchaseReferencePriceRow> {
}
