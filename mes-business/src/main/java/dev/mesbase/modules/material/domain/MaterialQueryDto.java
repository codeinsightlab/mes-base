// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.material.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 物料产品分页查询条件。
 */
public record MaterialQueryDto(
        @Min(0) Integer offset,
        @Min(1) @Max(100) Integer limit,
        @Size(max = 64) String code,
        @Size(max = 255) String name,
        @Size(max = 500) String specification,
        Long categoryId,
        Long unitId,
        @Size(max = 16) String materialType,
        @Size(max = 16) String status,
        @Size(max = 128) String externalId,
        @Size(max = 128) String externalCode
) {
}

