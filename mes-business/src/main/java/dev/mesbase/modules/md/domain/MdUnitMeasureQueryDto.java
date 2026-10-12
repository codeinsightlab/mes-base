// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.md.domain;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 计量单位分页查询条件。
 */
public record MdUnitMeasureQueryDto(
        @Min(0) Integer offset,
        @Min(1) @Max(100) Integer limit,
        @Size(max = 64) String factoryId,
        @Size(max = 64) String measureCode,
        @Size(max = 255) String measureName,
        @Size(max = 1) String primaryFlag,
        Long primaryId,
        @Digits(integer = 12, fraction = 6) BigDecimal changeRate,
        @Size(max = 1) String enableFlag,
        @Size(max = 500) String remark
) {
}

