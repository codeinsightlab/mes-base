package dev.mesbase.business.issue.controller.vo;

import java.time.LocalDateTime;

/**
 * 领料出库明细接口响应白名单。
 */
public record MesMaterialIssueItemVo(
        String id,

        String issueId,
        String materialId,
        String materialCode,
        String batchNo,
        String quantity,
        String unitId,
        String unitCode,
        String warehouseId,
        String warehouseCode,
        String remark,
        String createBy,
        LocalDateTime createTime,
        String updateBy,
        LocalDateTime updateTime
) {
}

