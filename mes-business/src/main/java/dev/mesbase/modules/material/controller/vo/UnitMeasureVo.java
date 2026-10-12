// MES-CODEGEN: scaffold; review required.
package dev.mesbase.modules.material.controller.vo;


/**
 * 计量单位接口响应白名单。
 */
public record UnitMeasureVo(
        String id,
        String factoryId,
        String code,
        String name,
        String symbol,
        String status
) {
}

