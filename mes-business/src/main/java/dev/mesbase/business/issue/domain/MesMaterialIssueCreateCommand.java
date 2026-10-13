// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.issue.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * 领料出库单写入参数，字段来自已审查的表结构。
 */
public record MesMaterialIssueCreateCommand(
        @NotBlank @Size(max = 64) String issueNo,
        Long workorderId,
        @Size(max = 64) String workorderCode,
        Long productId,
        @Size(max = 64) String productCode,
        @Size(max = 128) String flowCardNo,
        Long routingId,
        LocalDateTime issueTime,
        @NotBlank @Size(max = 16) String status,
        @Size(max = 500) String remark
) {
}

