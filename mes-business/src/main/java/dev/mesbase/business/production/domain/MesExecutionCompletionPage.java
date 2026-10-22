package dev.mesbase.business.production.domain;

import dev.mesbase.common.core.page.PageData;
import java.util.List;

/**
 * 正式生产完工事实查询结果，不包含 HTTP 展示转换。
 */
public record MesExecutionCompletionPage(
        List<MesExecutionCompletionEntity> items,
        long total,
        int offset,
        int limit
) implements PageData<MesExecutionCompletionEntity> {
}

