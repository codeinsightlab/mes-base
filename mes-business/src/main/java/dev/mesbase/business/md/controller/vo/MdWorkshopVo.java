// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.md.controller.vo;

import java.time.LocalDateTime;

/**
 * 车间接口响应白名单。
 */
public record MdWorkshopVo(
        String workshopId,
        String factoryId,
        String workshopCode,
        String workshopName,
        String area,
        String charge,
        String deptId,
        String enableFlag,
        String remark,
        String createBy,
        LocalDateTime createTime,
        String updateBy,
        LocalDateTime updateTime
) {
}

