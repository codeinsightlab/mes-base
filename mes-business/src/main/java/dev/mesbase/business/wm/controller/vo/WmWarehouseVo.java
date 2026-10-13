// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.wm.controller.vo;

import java.time.LocalDateTime;

/**
 * 仓库管理接口响应白名单。
 */
public record WmWarehouseVo(
        String id,
        String factoryId,
        String code,
        String name,
        String status,
        String remark,
        String createBy,
        LocalDateTime createTime,
        String updateBy,
        LocalDateTime updateTime
) {
}

