// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.issue.domain;

import java.util.List;

/**
 * 领料正式提交尝试及冻结载荷历史查询结果，不包含 HTTP 展示转换。
 */
public record MesMaterialIssueSubmissionPage(
        List<MesMaterialIssueSubmissionEntity> items,
        long total,
        int offset,
        int limit
) {
}

