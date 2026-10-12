// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.material.domain;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 安全库存配置分页查询条件。
 */
public record SafetyStockQueryDto(
        @Min(0) Integer offset,
        @Min(1) @Max(100) Integer limit,
        Long materialId,
        @Digits(integer = 14, fraction = 4) BigDecimal minQty,
        @Digits(integer = 14, fraction = 4) BigDecimal maxQty,
        @Size(max = 16) String status
) {
}

