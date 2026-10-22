package dev.mesbase.business.wm.domain;

import dev.mesbase.common.core.page.PageData;
import java.util.List;

/**
 * 库存管理查询结果，不包含 HTTP 展示转换。
 */
public record WmInventoryPage(
        List<WmInventoryItem> items,
        long total,
        int offset,
        int limit
) implements PageData<WmInventoryItem> {
}

