package dev.mesbase.business.basic.controller.vo;


/**
 * 物料产品接口响应白名单。
 */
public record MaterialVo(
        String id,

        String code,
        String name,
        String specification,
        String categoryId,
        String unitId,
        String status,
        String externalId,
        String externalCode
) {
}

