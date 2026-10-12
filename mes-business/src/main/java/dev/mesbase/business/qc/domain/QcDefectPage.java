// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.qc.domain;

import java.util.List;

/**
 * 质量缺陷查询结果，不包含 HTTP 展示转换。
 */
public record QcDefectPage(
        List<QcDefectEntity> items,
        long total,
        int offset,
        int limit
) {
}

