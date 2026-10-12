// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.pro.controller.vo;

import java.util.List;

/**
 * 生产工单分页响应。
 */
public record ProWorkorderPageVo(
        List<ProWorkorderVo> items,
        long total,
        int offset,
        int limit
) {
}

