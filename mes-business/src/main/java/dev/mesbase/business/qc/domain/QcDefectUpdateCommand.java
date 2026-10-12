// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.qc.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 质量缺陷写入参数，字段来自已审查的表结构。
 */
public record QcDefectUpdateCommand(
        @NotBlank @Size(max = 64) String factoryId,
        @NotBlank @Size(max = 64) String defectCode,
        @NotBlank @Size(max = 500) String defectName,
        @NotBlank @Size(max = 64) String indexType,
        @NotBlank @Size(max = 64) String defectLevel,
        @Size(max = 500) String remark
) {
}

