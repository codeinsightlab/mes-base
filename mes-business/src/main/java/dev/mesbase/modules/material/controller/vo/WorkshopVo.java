// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.material.controller.vo;


/**
 * 车间接口响应白名单。
 */
public record WorkshopVo(
        String id,
        String factoryId,
        String code,
        String name,
        String status,
        String remark
) {
}

