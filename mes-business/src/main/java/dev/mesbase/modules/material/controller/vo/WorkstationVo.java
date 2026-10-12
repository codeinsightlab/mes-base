// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.material.controller.vo;


/**
 * 工作站接口响应白名单。
 */
public record WorkstationVo(
        String id,
        String factoryId,
        String code,
        String name,
        String workshopId,
        String status,
        String remark
) {
}

