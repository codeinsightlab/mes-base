package dev.mesbase.framework.openapi;

import io.swagger.v3.oas.annotations.media.Schema;

public class OpenApiClientIdentityVo {
    @Schema(description = "当前 Token 对应的应用标识", example = "your_app_key", required = true)
    private final String appKey;

    public String getAppKey() {
        return appKey;
    }

    public OpenApiClientIdentityVo(String appKey) {
        this.appKey = appKey;
    }
}
