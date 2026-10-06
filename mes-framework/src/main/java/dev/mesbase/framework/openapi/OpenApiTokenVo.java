package dev.mesbase.framework.openapi;

import io.swagger.v3.oas.annotations.media.Schema;


public class OpenApiTokenVo {
    @Schema(description = "外部访问凭证", example = "your_access_token", required = true)
    private final String accessToken;
    @Schema(description = "认证方式", allowableValues = "Bearer", example = "Bearer", required = true)
    private final String tokenType = "Bearer";
    @Schema(description = "剩余有效期，单位秒；具体值由服务端配置决定", example = "7200", required = true)
    private final long expiresIn;

    public String getAccessToken() { return accessToken; }
    public String getTokenType() { return tokenType; }
    public long getExpiresIn() { return expiresIn; }
    public OpenApiTokenVo(String accessToken, long expiresIn) { this.accessToken = accessToken; this.expiresIn = expiresIn; }
}
