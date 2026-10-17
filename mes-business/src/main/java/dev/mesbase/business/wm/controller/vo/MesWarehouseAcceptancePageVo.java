// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.wm.controller.vo;

import java.util.List;

/**
 * 成品仓储接收事实分页响应。
 */
public record MesWarehouseAcceptancePageVo(
        List<MesWarehouseAcceptanceVo> items,
        long total,
        int offset,
        int limit
) {
}

