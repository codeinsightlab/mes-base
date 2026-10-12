// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.qc.controller.vo;

import java.util.List;

/**
 * 质量缺陷分页响应。
 */
public record QcDefectPageVo(
        List<QcDefectVo> items,
        long total,
        int offset,
        int limit
) {
}

