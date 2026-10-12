// MES-CODEGEN: scaffold; review required.
package dev.mesbase.modules.material.controller.vo;


/**
 * 安全库存配置接口响应白名单。
 */
public record SafetyStockVo(
        String id,
        String factoryId,
        String materialId,
        String minQty,
        String maxQty,
        String status
) {
}

