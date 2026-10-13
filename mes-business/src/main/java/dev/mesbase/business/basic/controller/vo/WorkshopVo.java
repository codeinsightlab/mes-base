package dev.mesbase.business.basic.controller.vo;


/**
 * 车间接口响应白名单。
 */
public record WorkshopVo(
        String id,

        String code,
        String name,
        String status,
        String remark
) {
}

