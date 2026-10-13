// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.basic.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 设备写入参数，字段来自已审查的表结构。
 */
public record EquipmentCreateCommand(
        @NotBlank @Size(max=64) String code,
        @NotBlank @Size(max=255) String name,
        @Size(max=255) String model,
        Long workstationId,
        @NotBlank @Size(max=16) String status,
        @Size(max=500) String remark
) {
}

