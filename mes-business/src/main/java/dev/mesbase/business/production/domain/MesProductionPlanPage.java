// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.domain;

import dev.mesbase.business.production.domain.MesProductionPlanEntity;
import dev.mesbase.common.core.page.PageData;
import java.util.List;

/**
 * 生产计划查询结果，不包含 HTTP 展示转换。
 */
public record MesProductionPlanPage(
        List<MesProductionPlanEntity> items,
        long total,
        int offset,
        int limit
) implements PageData<MesProductionPlanEntity> {
}

