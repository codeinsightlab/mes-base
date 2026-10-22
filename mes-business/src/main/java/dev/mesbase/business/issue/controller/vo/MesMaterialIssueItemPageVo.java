package dev.mesbase.business.issue.controller.vo;

import dev.mesbase.common.core.page.PageData;
import java.util.List;

/**
 * 领料出库明细分页响应。
 */
public record MesMaterialIssueItemPageVo(
        List<MesMaterialIssueItemVo> items,
        long total,
        int offset,
        int limit
) implements PageData<MesMaterialIssueItemVo> {
}

