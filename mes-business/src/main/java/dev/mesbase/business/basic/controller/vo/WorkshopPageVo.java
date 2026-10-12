// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.basic.controller.vo;

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

