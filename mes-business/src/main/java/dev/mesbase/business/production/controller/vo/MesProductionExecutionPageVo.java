package dev.mesbase.business.production.controller.vo;

import dev.mesbase.common.core.page.PageData;
import java.util.List;

/**
 * 生产执行单元分页响应。
 */
public record MesProductionExecutionPageVo(
        List<MesProductionExecutionVo> items,
        long total,
        int offset,
        int limit
) implements PageData<MesProductionExecutionVo> {
}

