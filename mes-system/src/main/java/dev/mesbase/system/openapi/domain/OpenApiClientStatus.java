package dev.mesbase.system.openapi.domain;

import dev.mesbase.common.exception.ServiceException;

public enum OpenApiClientStatus {
    DISABLED(0, "禁用"), ENABLED(1, "启用");
    private final int code;
    private final String name;

    OpenApiClientStatus(int code, String name) {
        this.code = code;
        this.name = name;
    }

    public int getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public static OpenApiClientStatus fromCode(Integer code) {
        for (OpenApiClientStatus value : values()) if (code != null && value.code == code) return value;
        throw new ServiceException("无效的client状态");
    }
}
