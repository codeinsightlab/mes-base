// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.sales.domain;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 销售订单明细写入参数，字段来自已审查的表结构。
 */
public record MesSalesOrderItemCreateCommand(
        @NotNull Long salesOrderId,
        @NotNull Integer lineNo,
        @NotNull Long materialId,
        @NotNull Long unitId,
        @NotNull @Digits(integer=14,fraction=6) BigDecimal orderedQty,
        LocalDate requiredDate,
        @Size(max=500) String remark,
        @NotNull Integer revision,
        Long createdBy,
        @NotNull LocalDateTime createdAt,
        Long updatedBy,
        @NotNull LocalDateTime updatedAt
) {
}

