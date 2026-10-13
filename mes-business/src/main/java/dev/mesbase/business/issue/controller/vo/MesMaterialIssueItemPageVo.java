// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.issue.controller.vo;

import java.util.List;

/**
 * 领料出库明细分页响应。
 */
public record MesMaterialIssueItemPageVo(
        List<MesMaterialIssueItemVo> items,
        long total,
        int offset,
        int limit
) {
}

