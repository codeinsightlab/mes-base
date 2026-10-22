package dev.mesbase.business.issue.controller.vo;

import dev.mesbase.common.core.page.PageData;
import java.util.List;

/**
 * 领料出库单分页响应。
 */
public record MesMaterialIssuePageVo(
        List<MesMaterialIssueVo> items,
        long total,
        int offset,
        int limit
) implements PageData<MesMaterialIssueVo> {
}

