// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.controller.vo;

import java.util.List;

/**
 * 工序能力人员分页响应。
 */
public record MesOperationUserPageVo(
        List<MesOperationUserVo> items,
        long total,
        int offset,
        int limit
) {
}

