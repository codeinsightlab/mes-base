// MES-CODEGEN: scaffold; review required.
package dev.mesbase.modules.material.controller.vo;

import java.util.List;

/**
 * 车间分页响应。
 */
public record WorkshopPageVo(
        List<WorkshopVo> items,
        long total,
        int offset,
        int limit
) {
}

