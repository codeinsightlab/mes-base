// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.domain;

import dev.mesbase.business.production.domain.MesProductionPlanItemEntity;
import dev.mesbase.common.core.page.PageData;
import java.util.List;

/**
 * 生产计划明细查询结果，不包含 HTTP 展示转换。
 */
public record MesProductionPlanItemPage(
        List<MesProductionPlanItemEntity> items,
        long total,
        int offset,
        int limit
) implements PageData<MesProductionPlanItemEntity> {
}

