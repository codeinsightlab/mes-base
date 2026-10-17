package dev.mesbase.business.shared.enums;

import java.util.LinkedHashMap;
import java.util.Map;

public enum BusinessSource {
    LOCAL("本地", "LOCAL"),
    EXTERNAL("外部ERP", "EXTERNAL");

    private final String description;
    private final String value;

    BusinessSource(String description, String value) {
        this.description = description;
        this.value = value;
    }

    /**
     * 未知值（包括 null）返回 null，由调用方决定拒绝或保留未知。
     */
    public static BusinessSource from(String value) {
        for (BusinessSource item : values()) {
            if (item.value.equals(value)) return item;
        }
        return null;
    }

    public static Map<String, String> toMap() {
        Map<String, String> result = new LinkedHashMap<>();
        for (BusinessSource item : values()) {
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
