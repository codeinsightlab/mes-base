package dev.mesbase.business.production.domain;

import dev.mesbase.common.core.page.PageData;
import java.util.List;

/**
 * 本地工单用料需求查询结果，不包含 HTTP 展示转换。
 */
public record MesWorkorderMaterialPage(
        List<MesWorkorderMaterialEntity> items,
        long total,
        int offset,
        int limit
) implements PageData<MesWorkorderMaterialEntity> {
}

