// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.resource.controller.vo;

import java.util.List;

/**
 * 生产人员分页响应。
 */
public record PersonnelPageVo(
        List<PersonnelVo> items,
        long total,
        int offset,
        int limit
) {
}

