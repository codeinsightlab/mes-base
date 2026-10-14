package dev.mesbase.business.issue.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * 领料ERP外部事实写入参数，字段来自已审查的表结构。
 */
public record MesMaterialIssueErpExtraCreateCommand(
        @NotNull Long id,
        @Size(max = 20) String sourceExecutionMode,
        @NotBlank @Size(max = 20) String externalEffect,
        @Size(max = 128) String externalBindingKey,
        @Size(max = 128) String externalReference,
        @Size(max = 20) String voidResultStatus,
        @Size(max = 20) String voidExecutionMode,
        @Size(max = 500) String voidMessage,
        @Size(max = 128) String provider,
        @Size(max = 128) String externalCode,
        @Size(max = 30) String documentStatus,
        @Size(max = 30) String lastOperation,
        @Size(max = 30) String lastOutcome,
        @Size(max = 500) String lastMessage,
        @NotNull Long operationVersion,
        LocalDateTime checkedAt
) {
}

