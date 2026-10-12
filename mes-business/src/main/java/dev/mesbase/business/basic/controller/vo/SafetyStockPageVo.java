// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.basic.controller.vo;

import java.util.List;

/**
 * 安全库存配置分页响应。
 */
public record SafetyStockPageVo(
        List<SafetyStockVo> items,
        long total,
        int offset,
        int limit
) {
}

