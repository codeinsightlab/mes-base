// MES-CODEGEN: scaffold; review required.
package dev.mesbase.modules.material.controller.vo;

import java.util.List;

/**
 * 工作站分页响应。
 */
public record WorkstationPageVo(
        List<WorkstationVo> items,
        long total,
        int offset,
        int limit
) {
}

