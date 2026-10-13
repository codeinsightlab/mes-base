// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.controller.vo;

import java.util.List;

/**
 * 工序分页响应。
 */
public record OperationPageVo(
        List<OperationVo> items,
        long total,
        int offset,
        int limit
) {
}

