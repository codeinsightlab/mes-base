// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.dv.sync;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 设备点检保养项目写入参数，字段来自已审查的表结构。
 */
public record ExternalDvSubject(
        @NotBlank @Size(max=64) String factoryId,
        @NotBlank @Size(max=64) String subjectCode,
        @Size(max=255) String subjectName,
        @Size(max=64) String subjectType,
        @NotBlank @Size(max=500) String subjectContent,
        @Size(max=255) String subjectStandard,
        @NotBlank @Size(max=1) String enableFlag,
        @Size(max=500) String remark
) {
}

