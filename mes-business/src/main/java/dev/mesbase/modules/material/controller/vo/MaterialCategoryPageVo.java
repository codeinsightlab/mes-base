// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.material.controller.vo;

import java.util.List;

/**
 * 物料产品分类分页响应。
 */
public record MaterialCategoryPageVo(
        List<MaterialCategoryVo> items,
        long total,
        int offset,
        int limit
) {
}

