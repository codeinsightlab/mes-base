// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.md.controller.vo;

import java.util.List;

/**
 * 计量单位分页响应。
 */
public record MdUnitMeasurePageVo(
        List<MdUnitMeasureVo> items,
        long total,
        int offset,
        int limit
) {
}

