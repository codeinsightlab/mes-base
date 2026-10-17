// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.wm.domain;

import java.util.List;

/**
 * 本地仓储不可变收发存动作事实查询结果，不包含 HTTP 展示转换。
 */
public record WmStockActionPage(
        List<WmStockActionEntity> items,
        long total,
        int offset,
        int limit
) {
}

