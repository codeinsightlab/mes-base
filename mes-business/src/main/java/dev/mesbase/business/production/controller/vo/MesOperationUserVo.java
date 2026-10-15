// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.controller.vo;

import java.time.LocalDateTime;

/**
 * 工序能力人员接口响应白名单。
 */
public record MesOperationUserVo(
        String id,
        String operationId,
        String userId,
        String remark,
        String createBy,
        LocalDateTime createTime
) {
}

