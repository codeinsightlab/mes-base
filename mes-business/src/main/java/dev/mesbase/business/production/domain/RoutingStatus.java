package dev.mesbase.business.production.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public enum RoutingStatus {
    DRAFT("草稿", "DRAFT"),
    PUBLISHED("已发布", "PUBLISHED"),
    DISABLED("停用", "DISABLED");

    private final String description;
    private final String value;

    RoutingStatus(String description, String value) {
        this.description = description;
        this.value = value;
    }

    /**
     * 未知值（包括 null）返回 null，由调用方决定拒绝或保留未知。
     */
    public static RoutingStatus from(String value) {
        for (RoutingStatus item : values()) {
            if (item.value.equals(value)) return item;
        }
        return null;
    }

    public static Map<String, String> toMap() {
        Map<String, String> result = new LinkedHashMap<>();
        for (RoutingStatus item : values()) {
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
