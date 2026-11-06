// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.purchase.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 供应商写入参数，字段来自已审查的表结构。
 */
public record PurchaseSupplierUpdateCommand(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 100) String contactPerson,
        @Size(max = 64) String contactPhone,
        @Size(max = 500) String address,
        @NotBlank @Size(max = 16) String status,
        @Size(max = 500) String remark
) {
}
