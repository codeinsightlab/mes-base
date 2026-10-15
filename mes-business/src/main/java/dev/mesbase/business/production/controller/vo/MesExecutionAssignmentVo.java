// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.controller.vo;

import java.time.LocalDateTime;

/**
 * 生产节点派工接口响应白名单。
 */
public record MesExecutionAssignmentVo(
        String id,
        String executionId,
        String routingNodeId,
        String userId,
        String assignedBy,
        LocalDateTime assignedAt,
        String remark
) {
}

