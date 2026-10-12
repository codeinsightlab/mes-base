// MES-CODEGEN: scaffold; review required.
package dev.mesbase.modules.material.domain;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 安全库存配置写入参数，字段来自已审查的表结构。
 */
public record SafetyStockUpdateCommand(
        @NotNull Long materialId,
        @NotNull @Digits(integer = 14, fraction = 4) BigDecimal minQty,
        @Digits(integer = 14, fraction = 4) BigDecimal maxQty,
        @NotBlank @Size(max = 16) String status
) {
}

