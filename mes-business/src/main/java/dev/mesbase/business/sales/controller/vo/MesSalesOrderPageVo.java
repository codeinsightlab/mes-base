// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.sales.controller.vo;

import dev.mesbase.business.sales.controller.vo.MesSalesOrderVo;
import dev.mesbase.common.core.page.PageData;
import java.util.List;

/**
 * 销售订单分页响应。
 */
public record MesSalesOrderPageVo(
        List<MesSalesOrderVo> items,
        long total,
        int offset,
        int limit
) implements PageData<MesSalesOrderVo> {
}

