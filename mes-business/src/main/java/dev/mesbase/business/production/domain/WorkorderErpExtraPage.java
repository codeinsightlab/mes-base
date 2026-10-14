// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.domain;

import java.util.List;

/**
 * 工单外部身份查询结果，不包含 HTTP 展示转换。
 */
public record WorkorderErpExtraPage(
        List<WorkorderErpExtraEntity> items,
        long total,
        int offset,
        int limit
) {
}

