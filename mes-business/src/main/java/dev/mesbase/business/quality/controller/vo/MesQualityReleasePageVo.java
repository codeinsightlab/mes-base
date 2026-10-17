// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.quality.controller.vo;

import java.util.List;

/**
 * 正式质量放行事实分页响应。
 */
public record MesQualityReleasePageVo(
        List<MesQualityReleaseVo> items,
        long total,
        int offset,
        int limit
) {
}

