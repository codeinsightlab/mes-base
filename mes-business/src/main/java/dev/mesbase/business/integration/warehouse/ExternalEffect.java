package dev.mesbase.business.integration.warehouse;

import java.util.LinkedHashMap;
import java.util.Map;

public enum ExternalEffect {
    NONE("无外部副作用", "NONE"),
    UNVERIFIED("未经核实", "UNVERIFIED"),
    CONFIRMED("已确认", "CONFIRMED"),
    REVERSED("已冲销", "REVERSED"),
    UNKNOWN("待核实", "UNKNOWN");

    private final String description;
    private final String value;

    ExternalEffect(String description, String value) {
        this.description = description;
        this.value = value;
    }

    /**
     * 未知值（包括 null）返回 null，由调用方决定拒绝或保留未知。
     */
    public static ExternalEffect from(String value) {
        for (ExternalEffect item : values()) {
            if (item.value.equals(value)) return item;
        }
        return null;
    }

    public static Map<String, String> toMap() {
        Map<String, String> result = new LinkedHashMap<>();
        for (ExternalEffect item : values()) {
            result.put(item.value, item.description);
        }
        return result;
    }

    public String getDescription() {
        return description;
    }

    public String getValue() {
        return value;
    }
}
