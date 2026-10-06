package dev.mesbase.framework.openapi;

import io.swagger.v3.oas.annotations.media.Schema;


public class OpenApiCapabilitiesVo {
    @Schema(description = "已授权且实际注册的接口标识，不代表全量业务数据权限", required = true)
    private final java.util.List<String> apiCodes;

    public java.util.List<String> getApiCodes() { return apiCodes; }
    public OpenApiCapabilitiesVo(java.util.List<String> apiCodes) { this.apiCodes = apiCodes; }
}
