package dev.mesbase.business.production.domain;

import dev.mesbase.common.core.page.PageData;
import java.util.List;

/**
 * 生产执行选定路径的有序节点引用查询结果，不包含 HTTP 展示转换。
 */
public record MesExecutionPathNodePage(
        List<MesExecutionPathNodeEntity> items,
        long total,
        int offset,
        int limit
) implements PageData<MesExecutionPathNodeEntity> {
}

