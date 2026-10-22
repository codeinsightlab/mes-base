package dev.mesbase.business.basic.domain;

import dev.mesbase.common.core.page.PageData;
import java.util.List;

/**
 * 安全库存配置查询结果，不包含 HTTP 展示转换。
 */
public record SafetyStockPage(
        List<SafetyStockEntity> items,
        long total,
        int offset,
        int limit
) implements PageData<SafetyStockEntity> {
}

