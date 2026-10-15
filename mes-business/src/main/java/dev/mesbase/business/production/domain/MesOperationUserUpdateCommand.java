// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.domain;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 工序能力人员写入参数，字段来自已审查的表结构。
 */
public record MesOperationUserUpdateCommand(
        @NotNull Long operationId,
        @NotNull Long userId,
        @Size(max = 500) String remark
) {
}

