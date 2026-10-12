// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.qc.controller.vo;

import java.time.LocalDateTime;

/**
 * 质量缺陷接口响应白名单。
 */
public record QcDefectVo(
        String defectId,
        String factoryId,
        String defectCode,
        String defectName,
        String indexType,
        String defectLevel,
        String remark,
        String createBy,
        LocalDateTime createTime,
        String updateBy,
        LocalDateTime updateTime
) {
}

