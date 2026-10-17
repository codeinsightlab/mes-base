// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.wm.domain;

import java.util.List;

/**
 * 成品仓储接收事实查询结果，不包含 HTTP 展示转换。
 */
public record MesWarehouseAcceptancePage(
        List<MesWarehouseAcceptanceEntity> items,
        long total,
        int offset,
        int limit
) {
}

