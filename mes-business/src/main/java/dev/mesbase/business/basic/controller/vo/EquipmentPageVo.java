// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.basic.controller.vo;

import java.util.List;

/**
 * 设备分页响应。
 */
public record EquipmentPageVo(
        List<EquipmentVo> items,
        long total,
        int offset,
        int limit
) {
}

