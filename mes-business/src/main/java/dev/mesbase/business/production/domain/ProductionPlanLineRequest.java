package dev.mesbase.business.production.domain;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductionPlanLineRequest(
        Long id,
        @NotNull Long materialId,
        @NotNull @DecimalMin(value = "0.000001") @Digits(integer = 14, fraction = 6) BigDecimal plannedQty,
        LocalDateTime plannedStartAt,
        LocalDateTime plannedEndAt,
        Integer priority,
        @Size(max = 500) String remark) {
}
