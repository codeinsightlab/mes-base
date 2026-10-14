// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.issue.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * 领料正式提交尝试及冻结载荷历史写入参数，字段来自已审查的表结构。
 */
public record MesMaterialIssueSubmissionCreateCommand(
        @NotNull Long issueId,
        @NotNull Integer pathRevision,
        @NotBlank @Size(max = 10000) String commandJson,
        @NotBlank @Size(max = 16) String status,
        @NotNull LocalDateTime createdAt,
        LocalDateTime finishedAt
) {
}

