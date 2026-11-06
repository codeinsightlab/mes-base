// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.purchase.controller.vo;

import dev.mesbase.business.purchase.controller.vo.PurchaseReferencePriceVo;
import dev.mesbase.common.core.page.PageData;
import java.util.List;

/**
 * 参考报价历史分页响应。
 */
public record PurchaseReferencePricePageVo(
        List<PurchaseReferencePriceVo> items,
        long total,
        int offset,
        int limit
) implements PageData<PurchaseReferencePriceVo> {
}
