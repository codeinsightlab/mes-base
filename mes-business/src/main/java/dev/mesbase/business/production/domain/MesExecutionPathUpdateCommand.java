// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * 生产执行当前完整路径写入参数，字段来自已审查的表结构。
 */
public record MesExecutionPathUpdateCommand(
        @NotNull Long routingId,
        @NotBlank @Size(max=16) String pathStatus,
        @NotNull Integer revision,
        @NotNull LocalDateTime selectedAt,
        LocalDateTime lockedAt
) {
}

