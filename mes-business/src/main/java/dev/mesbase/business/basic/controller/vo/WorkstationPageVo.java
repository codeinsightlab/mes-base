package dev.mesbase.business.basic.controller.vo;

import dev.mesbase.common.core.page.PageData;
import java.util.List;

/**
 * 工作站分页响应。
 */
public record WorkstationPageVo(
        List<WorkstationVo> items,
        long total,
        int offset,
        int limit
) implements PageData<WorkstationVo> {
}

