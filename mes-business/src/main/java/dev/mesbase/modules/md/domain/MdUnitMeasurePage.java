// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.md.domain;

import java.util.List;

/**
 * 计量单位查询结果，不包含 HTTP 展示转换。
 */
public record MdUnitMeasurePage(
        List<MdUnitMeasureEntity> items,
        long total,
        int offset,
        int limit
) {
}

