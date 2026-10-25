// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.sales.controller.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 销售订单接口响应白名单。
 */
public record MesSalesOrderVo(
        String id,
        String orderNo,
        String customerId,
        LocalDate orderDate,
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

