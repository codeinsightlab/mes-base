// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.issue.controller.vo;

import java.util.List;

/**
 * 领料出库单分页响应。
 */
public record MesMaterialIssuePageVo(
        List<MesMaterialIssueVo> items,
        long total,
        int offset,
        int limit
) {
}

