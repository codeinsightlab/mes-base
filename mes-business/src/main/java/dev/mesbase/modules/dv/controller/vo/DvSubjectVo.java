// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.dv.controller.vo;

import java.time.LocalDateTime;

/**
 * 设备点检保养项目接口响应白名单。
 */
public record DvSubjectVo(
        String subjectId,
        String factoryId,
        String subjectCode,
        String subjectName,
        String subjectType,
        String subjectContent,
        String subjectStandard,
        String enableFlag,
        String remark,
        String createBy,
        LocalDateTime createTime,
        String updateBy,
        LocalDateTime updateTime
) {
}

