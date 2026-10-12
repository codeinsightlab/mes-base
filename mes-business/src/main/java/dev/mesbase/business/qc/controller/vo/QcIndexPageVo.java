// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.qc.controller.vo;

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

