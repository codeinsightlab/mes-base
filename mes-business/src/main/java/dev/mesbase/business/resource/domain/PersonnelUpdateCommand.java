// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.resource.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 生产人员写入参数，字段来自已审查的表结构。
 */
public record PersonnelUpdateCommand(
        @NotBlank @Size(max=64) String code,
        @NotBlank @Size(max=255) String name,
        Long userId,
        @NotBlank @Size(max=16) String status
) {
}

