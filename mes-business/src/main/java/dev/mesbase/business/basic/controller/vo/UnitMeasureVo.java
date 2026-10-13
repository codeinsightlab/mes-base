package dev.mesbase.business.basic.controller.vo;


/**
 * 计量单位接口响应白名单。
 */
public record UnitMeasureVo(
        String id,

        String code,
        String name,
        String symbol,
        String status
) {
}

