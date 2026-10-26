// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.controller.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 生产计划接口响应白名单。
 */
public record MesProductionPlanVo(
        String id,
        String planNo,
        LocalDate planDate,
        String status,
        String remark,
        Integer revision,
        String createdBy,
        LocalDateTime createdAt,
        String updatedBy,
        LocalDateTime updatedAt
) {
}

