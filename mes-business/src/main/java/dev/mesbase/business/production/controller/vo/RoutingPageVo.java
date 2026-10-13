// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.controller.vo;

import java.util.List;

/**
 * 工艺路线分页响应。
 */
public record RoutingPageVo(
        List<RoutingVo> items,
        long total,
        int offset,
        int limit
) {
}

