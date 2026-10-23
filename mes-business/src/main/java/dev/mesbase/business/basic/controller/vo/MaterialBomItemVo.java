// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.basic.controller.vo;


/**
 * 组成组件接口响应白名单。
 */
public record MaterialBomItemVo(
        String id,
        String bomId,
        String materialId,
        String quantity,
        String unitId,
        String childBomId,
        Integer sort,
        String materialCode,
        String materialName,
        String specification,
        String unitName
) {
}

