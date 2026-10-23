// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.basic.domain;

import dev.mesbase.business.basic.domain.MaterialBomItemEntity;
import dev.mesbase.common.core.page.PageData;
import java.util.List;

/**
 * 组成组件查询结果，不包含 HTTP 展示转换。
 */
public record MaterialBomItemPage(
        List<MaterialBomItemRow> items,
        long total,
        int offset,
        int limit
) implements PageData<MaterialBomItemRow> {
}

