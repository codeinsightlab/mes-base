// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.basic.controller.vo;


/**
 * 设备接口响应白名单。
 */
public record EquipmentVo(
        String id,
        String factoryId,
        String code,
        String name,
        String model,
        String workstationId,
        String status,
        String remark
) {
}

