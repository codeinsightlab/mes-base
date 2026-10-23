package dev.mesbase.business.production.controller.vo;

import java.time.LocalDateTime;

/**
 * 生产执行单元接口响应白名单。
 */
public record MesProductionExecutionVo(
        String id,
        String workorderId,
        String productId,
        String productCode,
        String productName,
        String batchNo,
        String routingId,
        String targetQty,
        String status,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String remark,
        String createBy,
        LocalDateTime createTime,
        String updateBy,
        LocalDateTime updateTime,
        String routingCode, String routingName, Integer routingVersion, String createByName, String updateByName,
        String workorderStatus
) {
}

