// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.qc.controller.vo;

import java.util.List;

/**
 * 质量检测项分页响应。
 */
public record QcIndexPageVo(
        List<QcIndexVo> items,
        long total,
        int offset,
        int limit
) {
}

