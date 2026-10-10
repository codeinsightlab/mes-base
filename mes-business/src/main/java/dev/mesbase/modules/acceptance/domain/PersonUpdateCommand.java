// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.acceptance.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 人员写入参数，字段来自已审查的表结构。
 */
public record PersonUpdateCommand(
        @NotBlank @Size(max = 64) String name
) {
}

