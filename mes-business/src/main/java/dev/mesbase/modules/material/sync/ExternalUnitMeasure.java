// MES-CODEGEN: scaffold; review required.
package dev.mesbase.modules.material.sync;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 计量单位写入参数，字段来自已审查的表结构。
 */
public record ExternalUnitMeasure(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 32) String symbol,
        @NotBlank @Size(max = 16) String status
) {
}

