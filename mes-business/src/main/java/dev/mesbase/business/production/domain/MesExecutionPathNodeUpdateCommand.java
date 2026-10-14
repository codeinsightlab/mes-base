// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.domain;

import jakarta.validation.constraints.NotNull;

/**
 * 生产执行选定路径的有序节点引用写入参数，字段来自已审查的表结构。
 */
public record MesExecutionPathNodeUpdateCommand(
        @NotNull Long executionId,
        @NotNull Integer sequenceNo,
        @NotNull Long routingNodeId
) {
}

