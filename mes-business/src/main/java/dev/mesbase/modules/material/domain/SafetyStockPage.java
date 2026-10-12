// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.material.domain;

import java.util.List;

/**
 * 安全库存配置查询结果，不包含 HTTP 展示转换。
 */
public record SafetyStockPage(
        List<SafetyStockEntity> items,
        long total,
        int offset,
        int limit
) {
}

