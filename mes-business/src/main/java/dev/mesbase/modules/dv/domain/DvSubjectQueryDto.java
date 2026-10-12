// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.dv.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 设备点检保养项目分页查询条件。
 */
public record DvSubjectQueryDto(
        @Min(0) Integer offset,
        @Min(1) @Max(100) Integer limit,
        @Size(max = 64) String factoryId,
        @Size(max = 64) String subjectCode,
        @Size(max = 255) String subjectName,
        @Size(max = 64) String subjectType,
        @Size(max = 500) String subjectContent,
        @Size(max = 255) String subjectStandard,
        @Size(max = 1) String enableFlag,
        @Size(max = 500) String remark
) {
}

