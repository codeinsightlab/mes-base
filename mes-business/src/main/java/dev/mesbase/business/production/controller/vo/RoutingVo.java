package dev.mesbase.business.production.controller.vo;

import java.time.LocalDateTime;

/**
 * 工艺路线接口响应白名单。
 */
public record RoutingVo(
        String id,

        String code,
        String name,
        Integer version,
        String status,
        String description,
        String externalId,
        String sourceVersion,
        Integer revision,
        LocalDateTime publishedAt
) {
}

