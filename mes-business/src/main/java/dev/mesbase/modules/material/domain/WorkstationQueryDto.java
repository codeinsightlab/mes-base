// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.material.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 工作站分页查询条件。
 */
public record WorkstationQueryDto(
        @Min(0) Integer offset,
        @Min(1) @Max(100) Integer limit,
        @Size(max = 64) String code,
        @Size(max = 255) String name,
        Long workshopId,
        @Size(max = 16) String status,
        @Size(max = 500) String remark
) {
}

