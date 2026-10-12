// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.md.controller.vo;

import java.time.LocalDateTime;

/**
 * 计量单位接口响应白名单。
 */
public record MdUnitMeasureVo(
        String measureId,
        String factoryId,
        String measureCode,
        String measureName,
        String primaryFlag,
        String primaryId,
        String changeRate,
        String enableFlag,
        String remark,
        String createBy,
        LocalDateTime createTime,
        String updateBy,
        LocalDateTime updateTime
) {
}

