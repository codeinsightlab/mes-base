// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 生产计划写入参数，字段来自已审查的表结构。
 */
public record MesProductionPlanUpdateCommand(
        @NotBlank @Size(max=64) String planNo,
        @NotNull LocalDate planDate,
        @NotBlank @Size(max=16) String status,
        @Size(max=500) String remark,
        @NotNull Integer revision,
        Long createdBy,
        @NotNull LocalDateTime createdAt,
        Long updatedBy,
        @NotNull LocalDateTime updatedAt
) {
}

