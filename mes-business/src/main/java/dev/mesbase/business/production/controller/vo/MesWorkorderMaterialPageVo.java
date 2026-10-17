// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.controller.vo;

import java.util.List;

/**
 * 本地工单用料需求分页响应。
 */
public record MesWorkorderMaterialPageVo(
        List<MesWorkorderMaterialVo> items,
        long total,
        int offset,
        int limit
) {
}

