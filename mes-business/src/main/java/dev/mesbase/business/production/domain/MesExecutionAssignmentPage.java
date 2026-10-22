package dev.mesbase.business.production.domain;

import dev.mesbase.common.core.page.PageData;
import java.util.List;

/**
 * 生产节点派工查询结果，不包含 HTTP 展示转换。
 */
public record MesExecutionAssignmentPage(
        List<MesExecutionAssignmentEntity> items,
        long total,
        int offset,
        int limit
) implements PageData<MesExecutionAssignmentEntity> {
}

