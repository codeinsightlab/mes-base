// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.domain;

import java.util.List;

/**
 * 本地工单用料需求查询结果，不包含 HTTP 展示转换。
 */
public record MesWorkorderMaterialPage(
        List<MesWorkorderMaterialEntity> items,
        long total,
        int offset,
        int limit
) {
}

