// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.domain;

import java.util.List;

/**
 * 工艺路线查询结果，不包含 HTTP 展示转换。
 */
public record RoutingPage(
        List<RoutingEntity> items,
        long total,
        int offset,
        int limit
) {
}

