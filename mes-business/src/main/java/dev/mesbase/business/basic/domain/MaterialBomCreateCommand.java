package dev.mesbase.business.basic.domain;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record MaterialBomCreateCommand(@NotBlank @Size(max = 64) String code, @NotBlank @Size(max = 32) String version,
                                       @NotNull Long productId,
                                       @NotNull @Digits(integer = 14, fraction = 6) BigDecimal baseQty,
                                       @Size(max = 500) String remark) {
}
