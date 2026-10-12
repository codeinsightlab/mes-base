// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.md.domain;

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

