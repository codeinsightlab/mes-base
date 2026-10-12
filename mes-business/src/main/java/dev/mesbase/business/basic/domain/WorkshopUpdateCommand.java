// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.basic.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 车间写入参数，字段来自已审查的表结构。
 */
public record WorkshopUpdateCommand(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 16) String status,
        @Size(max = 500) String remark
) {
}

