// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.basic.domain;

import java.util.List;

/**
 * 计量单位查询结果，不包含 HTTP 展示转换。
 */
public record UnitMeasurePage(
        List<UnitMeasureEntity> items,
        long total,
        int offset,
        int limit
) {
}

