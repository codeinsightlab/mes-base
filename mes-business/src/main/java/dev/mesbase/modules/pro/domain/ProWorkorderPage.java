// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.pro.domain;

import java.util.List;

/**
 * 生产工单表查询结果，不包含 HTTP 展示转换。
 */
public record ProWorkorderPage(
        List<ProWorkorderEntity> items,
        long total,
        int offset,
        int limit
) {
}

