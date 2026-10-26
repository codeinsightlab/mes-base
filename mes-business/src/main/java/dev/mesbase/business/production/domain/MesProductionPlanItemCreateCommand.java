// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.domain;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产计划明细写入参数，字段来自已审查的表结构。
 */
public record MesProductionPlanItemCreateCommand(
        @NotNull Long planId,
        @NotNull Integer lineNo,
        @NotNull Long materialId,
        @NotNull @Digits(integer=14,fraction=6) BigDecimal plannedQty,
        LocalDateTime plannedStartAt,
        LocalDateTime plannedEndAt,
        @NotNull Integer priority,
        @Size(max=500) String remark,
        @NotNull Integer revision,
        Long createdBy,
        @NotNull LocalDateTime createdAt,
        Long updatedBy,
        @NotNull LocalDateTime updatedAt
) {
}

