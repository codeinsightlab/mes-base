package dev.mesbase.business.basic.domain;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record MaterialBomItemUpdateCommand(@NotNull Long materialId,
                                           @NotNull @Digits(integer = 14, fraction = 6) BigDecimal quantity,
                                           Long childBomId, @NotNull Integer sort) {
}
