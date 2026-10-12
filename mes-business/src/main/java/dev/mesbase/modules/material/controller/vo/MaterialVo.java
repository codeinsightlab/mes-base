// MES-CODEGEN: scaffold; review required.
package dev.mesbase.modules.material.controller.vo;


/**
 * 物料产品接口响应白名单。
 */
public record MaterialVo(
        String id,
        String factoryId,
        String code,
        String name,
        String specification,
        String categoryId,
        String unitId,
        String materialType,
        String status,
        String externalId,
        String externalCode
) {
}

