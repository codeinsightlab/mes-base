// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.resource.controller.vo;


/**
 * 生产人员接口响应白名单。
 */
public record PersonnelVo(
        String id,
        String factoryId,
        String code,
        String name,
        String userId,
        String status
) {
}

