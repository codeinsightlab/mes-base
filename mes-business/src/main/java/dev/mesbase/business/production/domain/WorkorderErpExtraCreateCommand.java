package dev.mesbase.business.production.domain;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 工单外部身份写入参数，字段来自已审查的表结构。
 */
public record WorkorderErpExtraCreateCommand(
        @NotNull Long id,
        @Size(max = 128) String externalId,
        @Size(max = 128) String externalCode,
        @Size(max = 128) String provider
) {
}

