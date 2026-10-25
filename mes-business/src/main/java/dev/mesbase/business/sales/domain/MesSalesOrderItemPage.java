// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.sales.domain;

import dev.mesbase.business.sales.domain.MesSalesOrderItemEntity;
import dev.mesbase.common.core.page.PageData;
import java.util.List;

/**
 * 销售订单明细查询结果，不包含 HTTP 展示转换。
 */
public record MesSalesOrderItemPage(
        List<MesSalesOrderItemEntity> items,
        long total,
        int offset,
        int limit
) implements PageData<MesSalesOrderItemEntity> {
}

