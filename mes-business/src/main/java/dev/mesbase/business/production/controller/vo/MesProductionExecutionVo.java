// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.controller.vo;

import java.time.LocalDateTime;

/**
 * 生产执行单元接口响应白名单。
 */
public record MesProductionExecutionVo(
        String id,
        String executionNo,
        String workorderId,
        String productId,
        String routingId,
        String targetQty,
        String status,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String remark,
        String createBy,
        LocalDateTime createTime,
        String updateBy,
        LocalDateTime updateTime
) {
}

