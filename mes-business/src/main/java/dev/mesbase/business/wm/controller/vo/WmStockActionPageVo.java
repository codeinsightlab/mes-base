package dev.mesbase.business.wm.controller.vo;

import dev.mesbase.common.core.page.PageData;
import java.util.List;

/**
 * 本地仓储不可变收发存动作事实分页响应。
 */
public record WmStockActionPageVo(
        List<WmStockActionVo> items,
        long total,
        int offset,
        int limit
) implements PageData<WmStockActionVo> {
}

