// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.qc.controller.vo;

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

