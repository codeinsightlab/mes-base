// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.material.controller.vo;


/**
 * 物料产品分类接口响应白名单。
 */
public record MaterialCategoryVo(
        String id,
        String factoryId,
        String code,
        String name,
        String parentId,
        Integer sort,
        String status,
        String remark
) {
}

