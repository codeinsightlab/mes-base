// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.pro.controller.vo;

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

