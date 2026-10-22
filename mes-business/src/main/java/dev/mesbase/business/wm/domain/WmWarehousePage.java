package dev.mesbase.business.wm.domain;

import dev.mesbase.common.core.page.PageData;
import java.util.List;

/**
 * 仓库管理查询结果，不包含 HTTP 展示转换。
 */
public record WmWarehousePage(
        List<WmWarehouseEntity> items,
        long total,
        int offset,
        int limit
) implements PageData<WmWarehouseEntity> {
}

