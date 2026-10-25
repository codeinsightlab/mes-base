// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.sales.controller.vo;

import dev.mesbase.business.sales.controller.vo.MesCustomerVo;
import dev.mesbase.common.core.page.PageData;
import java.util.List;

/**
 * 销售客户分页响应。
 */
public record MesCustomerPageVo(
        List<MesCustomerVo> items,
        long total,
        int offset,
        int limit
) implements PageData<MesCustomerVo> {
}

