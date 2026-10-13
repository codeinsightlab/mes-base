// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.wm.controller.vo;

import java.util.List;

/**
 * 仓库管理分页响应。
 */
public record WmWarehousePageVo(
        List<WmWarehouseVo> items,
        long total,
        int offset,
        int limit
) {
}

