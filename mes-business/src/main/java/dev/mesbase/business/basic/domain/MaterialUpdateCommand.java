// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.basic.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 物料产品写入参数，字段来自已审查的表结构。
 */
public record MaterialUpdateCommand(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 500) String specification,
        Long categoryId,
        @NotNull Long unitId,
        @NotBlank @Size(max = 16) String materialType,
        @NotBlank @Size(max = 16) String status,
        @Size(max = 128) String externalId,
        @Size(max = 128) String externalCode
) {
}

