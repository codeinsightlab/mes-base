// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.sales.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * 销售客户写入参数，字段来自已审查的表结构。
 */
public record MesCustomerCreateCommand(
        @NotBlank @Size(max=64) String code,
        @NotBlank @Size(max=255) String name,
        @Size(max=128) String shortName,
        @Size(max=128) String contactPerson,
        @Size(max=64) String contactPhone,
        @Size(max=500) String address,
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

