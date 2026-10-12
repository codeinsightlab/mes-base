package dev.mesbase.system.openapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class OpenApiCredentialRequest {
    @Schema(description = "管理员分配的应用标识", example = "your_app_key", required = true)
    @NotBlank
    @Size(max = 64)
    private String appKey;
    @Schema(description = "创建或重置时一次性领取的 Secret，只在申请 Token 时使用", example = "your_app_secret", required = true)
    @NotBlank
    @Size(max = 128)
    private String appSecret;

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }
}
