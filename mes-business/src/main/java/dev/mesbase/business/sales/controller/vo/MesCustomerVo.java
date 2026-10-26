// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.sales.controller.vo;

import java.time.LocalDateTime;

/**
 * 销售客户接口响应白名单。
 */
public record MesCustomerVo(
        String id,
        String code,
        String name,
        String shortName,
        String contactPerson,
        String contactPhone,
        String address,
        String status,
        String sourceType,
        String externalId,
        String remark,
        Integer revision,
        String createdBy,
        LocalDateTime createdAt,
        String updatedBy,
        LocalDateTime updatedAt
) {
}

