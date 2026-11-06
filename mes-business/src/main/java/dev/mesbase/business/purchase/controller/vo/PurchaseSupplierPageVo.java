// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.purchase.controller.vo;

import dev.mesbase.business.purchase.controller.vo.PurchaseSupplierVo;
import dev.mesbase.common.core.page.PageData;
import java.util.List;

/**
 * 供应商分页响应。
 */
public record PurchaseSupplierPageVo(
        List<PurchaseSupplierVo> items,
        long total,
        int offset,
        int limit
) implements PageData<PurchaseSupplierVo> {
}
