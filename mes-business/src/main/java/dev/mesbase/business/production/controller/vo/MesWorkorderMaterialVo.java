// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.controller.vo;

import java.time.LocalDateTime;

/**
 * 本地工单用料需求接口响应白名单。
 */
public record MesWorkorderMaterialVo(
        String id,
        String materialId,
        String unitId,
        String totalRequiredQty,
        String plannedQty,
        String warehouseIds,
        String workCenter,
        String revision,
        String status,
        String updatedBy,
        LocalDateTime updatedAt
) {
}

