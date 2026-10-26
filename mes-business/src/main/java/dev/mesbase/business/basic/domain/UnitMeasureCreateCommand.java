package dev.mesbase.business.basic.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 计量单位写入参数，字段来自已审查的表结构。
 */
public record UnitMeasureCreateCommand(
        @NotBlank @Size(max=64) String code,
        @NotBlank @Size(max=255) String name,
        @Size(max=32) String symbol,
        @NotBlank @Size(max=16) String status,
        @NotNull @Min(0) @Max(1) Integer mustBeWholeNumber,
        @NotNull @Min(0) @Max(6) Integer quantityPrecision
) {
}

