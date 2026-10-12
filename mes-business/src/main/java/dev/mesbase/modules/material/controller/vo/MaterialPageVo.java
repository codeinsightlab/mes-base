// MES-CODEGEN: scaffold; review required.
package dev.mesbase.modules.material.controller.vo;

import java.util.List;

/**
 * 物料产品分页响应。
 */
public record MaterialPageVo(
        List<MaterialVo> items,
        long total,
        int offset,
        int limit
) {
}

