package dev.mesbase.business.purchase.controller.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 参考报价历史接口响应白名单。
 */
public record PurchaseReferencePriceVo(
        String id,
        String supplierId,
        String materialId,
        String unitId,
        String unitPrice,
        String currency,
        LocalDate effectiveDate,
        String createdBy,
        LocalDateTime createdAt,
        String remark,
        String supplierCode, String supplierName, String materialCode, String materialName, String unitName
) {
}
