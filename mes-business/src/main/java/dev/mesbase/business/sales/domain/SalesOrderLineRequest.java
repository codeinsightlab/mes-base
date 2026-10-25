package dev.mesbase.business.sales.domain;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SalesOrderLineRequest(
        Long id,
        @NotNull Long materialId,
        @NotNull @DecimalMin(value = "0.000001") @Digits(integer = 14, fraction = 6) BigDecimal orderedQty,
        LocalDate requiredDate,
        @Size(max = 500) String remark) {
}
