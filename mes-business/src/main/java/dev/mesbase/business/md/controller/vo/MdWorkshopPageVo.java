// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.md.controller.vo;

import java.util.List;

/**
 * 车间分页响应。
 */
public record MdWorkshopPageVo(
        List<MdWorkshopVo> items,
        long total,
        int offset,
        int limit
) {
}

