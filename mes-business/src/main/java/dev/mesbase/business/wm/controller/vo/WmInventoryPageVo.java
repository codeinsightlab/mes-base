package dev.mesbase.business.wm.controller.vo;

import dev.mesbase.common.core.page.PageData;
import java.util.List;

/**
 * 库存管理分页响应。
 */
public record WmInventoryPageVo(
        List<WmInventoryVo> items,
        long total,
        int offset,
        int limit
) implements PageData<WmInventoryVo> {
}

