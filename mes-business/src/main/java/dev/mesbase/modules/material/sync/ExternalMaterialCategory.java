// MES-CODEGEN: scaffold; review required.
package dev.mesbase.modules.material.sync;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 物料产品分类写入参数，字段来自已审查的表结构。
 */
public record ExternalMaterialCategory(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 255) String name,
        Long parentId,
        @NotNull Integer sort,
        @NotBlank @Size(max = 16) String status,
        @Size(max = 500) String remark
) {
}

