// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.controller.vo;

import java.time.LocalDateTime;

/**
 * 生产计划明细接口响应白名单。
 */
public record MesProductionPlanItemVo(
        String id,
        String planId,
        Integer lineNo,
        String materialId,
        String plannedQty,
        LocalDateTime plannedStartAt,
        LocalDateTime plannedEndAt,
        Integer priority,
        String remark,
        Integer revision,
        String createdBy,
        LocalDateTime createdAt,
        String updatedBy,
        LocalDateTime updatedAt
) {
}

