package dev.mesbase.business.basic.controller.vo;

import dev.mesbase.common.core.page.PageData;
import java.util.List;

/**
 * 计量单位分页响应。
 */
public record UnitMeasurePageVo(
        List<UnitMeasureVo> items,
        long total,
        int offset,
        int limit
) implements PageData<UnitMeasureVo> {
}

