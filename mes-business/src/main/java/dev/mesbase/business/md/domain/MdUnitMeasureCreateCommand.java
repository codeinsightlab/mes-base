// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.md.domain;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 计量单位写入参数，字段来自已审查的表结构。
 */
public record MdUnitMeasureCreateCommand(
        @NotBlank @Size(max = 64) String factoryId,
        @NotBlank @Size(max = 64) String measureCode,
        @NotBlank @Size(max = 255) String measureName,
        @NotBlank @Size(max = 1) String primaryFlag,
        Long primaryId,
        @Digits(integer = 12, fraction = 6) BigDecimal changeRate,
        @NotBlank @Size(max = 1) String enableFlag,
        @Size(max = 500) String remark
) {
}

