package dev.mesbase.business.basic.controller.vo;


/**
 * 安全库存配置接口响应白名单。
 */
public record SafetyStockVo(
        String id,

        String materialId,
        String minQty,
        String maxQty,
        String status
) {
}

