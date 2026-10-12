// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.md.domain;

import java.util.List;

/**
 * 车间查询结果，不包含 HTTP 展示转换。
 */
public record MdWorkshopPage(
        List<MdWorkshopEntity> items,
        long total,
        int offset,
        int limit
) {
}

