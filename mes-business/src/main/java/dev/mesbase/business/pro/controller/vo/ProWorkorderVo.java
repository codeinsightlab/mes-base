// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.pro.controller.vo;

import java.time.LocalDateTime;

/**
 * 生产工单接口响应白名单。
 */
public record ProWorkorderVo(
        String workorderId,
        String workorderCode,
        String workorderName,
        String orderSource,
        String sourceCode,
        String productId,
        String productCode,
        String productName,
        String productSpc,
        String unitOfMeasure,
        Double quantity,
        Double quantityProduced,
        Double quantityChanged,
        Double quantityScheduled,
        String clientId,
        String clientCode,
        String clientName,
        String batchCode,
        LocalDateTime requestDate,
        String parentId,
        String ancestors,
        LocalDateTime finishDate,
        String status,
        String remark,
        String attr1,
        String attr2,
        Integer attr3,
        Integer attr4,
        String createBy,
        LocalDateTime createTime,
        String updateBy,
        LocalDateTime updateTime,
        LocalDateTime transactionDate,
        String moId
) {
}

