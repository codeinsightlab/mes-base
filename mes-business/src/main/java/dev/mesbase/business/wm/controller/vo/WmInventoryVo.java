package dev.mesbase.business.wm.controller.vo;

import java.time.LocalDateTime;

/**
 * 库存管理接口响应白名单。
 */
public record WmInventoryVo(
        String id,
        String factoryId,
        String materialId,
        String warehouseId,
        String materialName,
        String warehouseName,
        String quantity,
        String availableQuantity,
        String status,
        String remark,
        String createBy,
        LocalDateTime createTime,
        String updateBy,
        LocalDateTime updateTime
) {
}

