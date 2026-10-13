package dev.mesbase.business.issue.controller.vo;

import java.time.LocalDateTime;

/**
 * 领料出库单接口响应白名单。
 */
public record MesMaterialIssueVo(
        String id,

        String issueNo,
        String workorderId,
        String workorderCode,
        String productId,
        String productCode,
        String flowCardNo,
        String routingId,
        LocalDateTime issueTime,
        String status,
        String remark,
        String createBy,
        LocalDateTime createTime,
        String updateBy,
        LocalDateTime updateTime
) {
}

