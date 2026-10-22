package dev.mesbase.business.basic.controller.vo;

import dev.mesbase.common.core.page.PageData;
import java.util.List;

/**
 * 物料产品分类分页响应。
 */
public record MaterialCategoryPageVo(
        List<MaterialCategoryVo> items,
        long total,
        int offset,
        int limit
) implements PageData<MaterialCategoryVo> {
}

