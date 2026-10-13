// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.controller.vo;


/**
 * 工序接口响应白名单。
 */
public record OperationVo(
        String id,
        String factoryId,
        String code,
        String name,
        String description,
        String status,
        Integer sort,
        String externalId,
        Integer revision
) {
}

