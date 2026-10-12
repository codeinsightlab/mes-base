// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.material.sync;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 物料产品写入参数，字段来自已审查的表结构。
 */
public record ExternalMaterial(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 500) String specification,
        @Size(max = 64) String categoryCode,
        @NotBlank @Size(max = 64) String unitCode,
        @NotBlank @Size(max = 16) String materialType,
        @NotBlank @Size(max = 16) String status,
        @Size(max = 128) String externalId,
        @Size(max = 128) String externalCode
) {
}

