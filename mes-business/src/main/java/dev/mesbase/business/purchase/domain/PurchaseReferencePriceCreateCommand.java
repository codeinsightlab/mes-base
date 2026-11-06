// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.purchase.domain;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 参考报价历史写入参数，字段来自已审查的表结构。
 */
public record PurchaseReferencePriceCreateCommand(
        @NotNull Long supplierId,
        @NotNull Long materialId,
        @NotNull Long unitId,
        @NotNull @Digits(integer = 14, fraction = 6) BigDecimal unitPrice,
        @NotBlank @Size(max = 3) String currency,
        @NotNull LocalDate effectiveDate,
        @Size(max = 500) String remark
) {
}
