// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.material.controller.vo;

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

