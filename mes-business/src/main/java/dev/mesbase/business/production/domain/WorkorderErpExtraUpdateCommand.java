package dev.mesbase.business.production.domain;

import jakarta.validation.constraints.Size;

/**
 * 工单外部身份写入参数，字段来自已审查的表结构。
 */
public record WorkorderErpExtraUpdateCommand(
        @Size(max = 128) String externalId,
        @Size(max = 128) String externalCode,
        @Size(max = 128) String provider
) {
}

