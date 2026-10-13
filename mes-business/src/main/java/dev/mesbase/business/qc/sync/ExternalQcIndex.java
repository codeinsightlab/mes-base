// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.qc.sync;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 质量检测项写入参数，字段来自已审查的表结构。
 */
public record ExternalQcIndex(
        @NotBlank @Size(max=64) String factoryId,
        @NotBlank @Size(max=64) String indexCode,
        @NotBlank @Size(max=255) String indexName,
        @NotBlank @Size(max=64) String indexType,
        @Size(max=255) String qcTool,
        @Size(max=500) String remark
) {
}

