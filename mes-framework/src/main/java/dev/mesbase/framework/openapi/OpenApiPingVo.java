package dev.mesbase.framework.openapi;

import io.swagger.v3.oas.annotations.media.Schema;

public class OpenApiPingVo {
    @Schema(description = "连通性标识", example = "pong", required = true)
    private final String message;
    @Schema(description = "服务端 UTC 时间", format = "date-time", example = "2026-10-05T00:00:00Z", required = true)
    private final String serverTime;

    public String getMessage() {
        return message;
    }

    public String getServerTime() {
        return serverTime;
    }

    public OpenApiPingVo(String message, String serverTime) {
        this.message = message;
        this.serverTime = serverTime;
    }
}
