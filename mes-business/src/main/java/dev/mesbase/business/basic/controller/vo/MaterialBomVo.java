// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.basic.controller.vo;

import java.time.LocalDateTime;

/**
 * 物料组成版本接口响应白名单。
 */
public record MaterialBomVo(
        String id,
        String code,
        String version,
        String productId,
        String baseQty,
        String unitId,
        String status,
        Boolean defaultVersion,
        String revision,
        String remark,
        String createdBy,
        LocalDateTime createdAt,
        LocalDateTime publishedAt
) {
}
