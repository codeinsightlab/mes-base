// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.sales.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 销售订单写入参数，字段来自已审查的表结构。
 */
public record MesSalesOrderCreateCommand(
        @NotBlank @Size(max=64) String orderNo,
        @NotNull Long customerId,
        @NotNull LocalDate orderDate,
        @NotBlank @Size(max=16) String status,
        @NotBlank @Size(max=16) String sourceType,
        @Size(max=128) String externalId,
        @Size(max=500) String remark,
        @NotNull Integer revision,
        Long createdBy,
        @NotNull LocalDateTime createdAt,
        Long updatedBy,
        @NotNull LocalDateTime updatedAt
) {
}

