// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.qc.domain;

import java.util.List;

/**
 * 质量检测项查询结果，不包含 HTTP 展示转换。
 */
public record QcIndexPage(
        List<QcIndexEntity> items,
        long total,
        int offset,
        int limit
) {
}

