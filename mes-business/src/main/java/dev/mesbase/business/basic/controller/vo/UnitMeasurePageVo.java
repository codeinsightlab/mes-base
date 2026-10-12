// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.basic.controller.vo;

import java.util.List;

/**
 * 计量单位分页响应。
 */
public record UnitMeasurePageVo(
        List<UnitMeasureVo> items,
        long total,
        int offset,
        int limit
) {
}

