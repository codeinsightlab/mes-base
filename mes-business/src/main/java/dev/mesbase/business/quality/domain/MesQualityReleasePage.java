// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.quality.domain;

import java.util.List;

/**
 * 正式质量放行事实查询结果，不包含 HTTP 展示转换。
 */
public record MesQualityReleasePage(
        List<MesQualityReleaseEntity> items,
        long total,
        int offset,
        int limit
) {
}

