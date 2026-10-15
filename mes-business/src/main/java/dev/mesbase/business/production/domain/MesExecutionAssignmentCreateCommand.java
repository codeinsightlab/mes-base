package dev.mesbase.business.production.domain;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 生产节点派工写入参数，字段来自已审查的表结构。
 */
public record MesExecutionAssignmentCreateCommand(
        @NotNull Long executionId,
        @NotNull Long routingNodeId,
        @NotNull Long userId,
        @Size(max = 500) String remark
) {
}

