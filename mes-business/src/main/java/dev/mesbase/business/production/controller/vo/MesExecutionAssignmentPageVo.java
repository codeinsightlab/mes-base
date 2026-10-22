package dev.mesbase.business.production.controller.vo;

import dev.mesbase.common.core.page.PageData;
import java.util.List;

/**
 * 生产节点派工分页响应。
 */
public record MesExecutionAssignmentPageVo(
        List<MesExecutionAssignmentVo> items,
        long total,
        int offset,
        int limit
) implements PageData<MesExecutionAssignmentVo> {
}

