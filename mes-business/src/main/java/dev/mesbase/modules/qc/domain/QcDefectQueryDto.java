// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.qc.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 质量缺陷分页查询条件。
 */
public record QcDefectQueryDto(
        @Min(0) Integer offset,
        @Min(1) @Max(100) Integer limit,
        @Size(max = 64) String factoryId,
        @Size(max = 64) String defectCode,
        @Size(max = 500) String defectName,
        @Size(max = 64) String indexType,
        @Size(max = 64) String defectLevel,
        @Size(max = 500) String remark
) {
}

