package dev.mesbase.business.issue.domain;

import dev.mesbase.common.core.page.PageData;
import java.util.List;

/**
 * 领料出库明细查询结果，不包含 HTTP 展示转换。
 */
public record MesMaterialIssueItemPage(
        List<MesMaterialIssueItemEntity> items,
        long total,
        int offset,
        int limit
) implements PageData<MesMaterialIssueItemEntity> {
}

