// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.controller.vo;

import dev.mesbase.business.production.controller.vo.MesProductionPlanItemVo;
import dev.mesbase.common.core.page.PageData;
import java.util.List;

/**
 * 生产计划明细分页响应。
 */
public record MesProductionPlanItemPageVo(
        List<MesProductionPlanItemVo> items,
        long total,
        int offset,
        int limit
) implements PageData<MesProductionPlanItemVo> {
}

