// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.issue.domain;

import java.util.List;

/**
 * 领料出库单查询结果，不包含 HTTP 展示转换。
 */
public record MesMaterialIssuePage(
        List<MesMaterialIssueEntity> items,
        long total,
        int offset,
        int limit
) {
}

