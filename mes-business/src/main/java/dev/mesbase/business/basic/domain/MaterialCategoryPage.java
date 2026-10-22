package dev.mesbase.business.basic.domain;

import dev.mesbase.common.core.page.PageData;
import java.util.List;

/**
 * 物料产品分类查询结果，不包含 HTTP 展示转换。
 */
public record MaterialCategoryPage(
        List<MaterialCategoryEntity> items,
        long total,
        int offset,
        int limit
) implements PageData<MaterialCategoryEntity> {
}

