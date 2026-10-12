// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.qc.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 质量检测项分页查询条件。
 */
public record QcIndexQueryDto(
        @Min(0) Integer offset,
        @Min(1) @Max(100) Integer limit,
        @Size(max = 64) String factoryId,
        @Size(max = 64) String indexCode,
        @Size(max = 255) String indexName,
        @Size(max = 64) String indexType,
        @Size(max = 255) String qcTool,
        @Size(max = 500) String remark
) {
}

