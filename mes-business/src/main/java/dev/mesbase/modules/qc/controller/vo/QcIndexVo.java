// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.qc.controller.vo;

import java.time.LocalDateTime;

/**
 * 质量检测项接口响应白名单。
 */
public record QcIndexVo(
        String indexId,
        String factoryId,
        String indexCode,
        String indexName,
        String indexType,
        String qcTool,
        String remark,
        String createBy,
        LocalDateTime createTime,
        String updateBy,
        LocalDateTime updateTime
) {
}

