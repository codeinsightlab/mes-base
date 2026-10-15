package dev.mesbase.business.shared.enums;

import java.util.LinkedHashMap;
import java.util.Map;

public enum EnableStatus {
    ENABLED("启用", "ENABLED"),
    DISABLED("停用", "DISABLED");

    private final String description;
    private final String value;

    EnableStatus(String description, String value) {
        this.description = description;
        this.value = value;
    }

    /**
     * 未知值（包括 null）返回 null，由调用方决定拒绝或保留未知。
     */
    public static EnableStatus from(String value) {
        for (EnableStatus item : values()) {
            if (item.value.equals(value)) return item;
        }
        return null;
    }

    public static Map<String, String> toMap() {
        Map<String, String> result = new LinkedHashMap<>();
        for (EnableStatus item : values()) {
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
