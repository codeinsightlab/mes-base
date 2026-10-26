// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.sales.controller.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 销售订单明细接口响应白名单。
 */
public record MesSalesOrderItemVo(
        String id,
        String salesOrderId,
        Integer lineNo,
        String materialId,
        String unitId,
        String orderedQty,
        LocalDate requiredDate,
        String remark,
        Integer revision,
        String createdBy,
        LocalDateTime createdAt,
        String updatedBy,
        LocalDateTime updatedAt
) {
}

