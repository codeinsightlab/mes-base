// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.purchase.controller.vo;

import java.time.LocalDateTime;

/**
 * 供应商接口响应白名单。
 */
public record PurchaseSupplierVo(
        String id,
        String code,
        String name,
        String contactPerson,
        String contactPhone,
        String address,
        String status,
        String remark,
        String createdBy,
        LocalDateTime createdAt,
        String updatedBy,
        LocalDateTime updatedAt
) {
}
