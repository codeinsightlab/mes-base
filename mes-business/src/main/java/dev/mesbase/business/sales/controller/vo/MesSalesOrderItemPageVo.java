// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.sales.controller.vo;

import dev.mesbase.business.sales.controller.vo.MesSalesOrderItemVo;
import dev.mesbase.common.core.page.PageData;
import java.util.List;

/**
 * 销售订单明细分页响应。
 */
public record MesSalesOrderItemPageVo(
        List<MesSalesOrderItemVo> items,
        long total,
        int offset,
        int limit
) implements PageData<MesSalesOrderItemVo> {
}

