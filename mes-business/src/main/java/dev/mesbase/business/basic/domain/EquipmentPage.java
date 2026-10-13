// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.basic.domain;

import java.util.List;

/**
 * 设备查询结果，不包含 HTTP 展示转换。
 */
public record EquipmentPage(
        List<EquipmentEntity> items,
        long total,
        int offset,
        int limit
) {
}

