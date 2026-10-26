// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.controller.vo;

import dev.mesbase.business.production.controller.vo.MesProductionPlanVo;
import dev.mesbase.common.core.page.PageData;
import java.util.List;

/**
 * 生产计划分页响应。
 */
public record MesProductionPlanPageVo(
        List<MesProductionPlanVo> items,
        long total,
        int offset,
        int limit
) implements PageData<MesProductionPlanVo> {
}

