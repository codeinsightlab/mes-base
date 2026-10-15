package dev.mesbase.business.production.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public enum ExecutionPathStatus {
    UNSELECTED("未选择", "UNSELECTED"),
    SELECTED("已选择", "SELECTED"),
    LOCKED("已确认锁定", "LOCKED");

    private final String description;
    private final String value;

    ExecutionPathStatus(String description, String value) {
        this.description = description;
        this.value = value;
    }

    /**
     * 未知值（包括 null）返回 null，由调用方决定拒绝或保留未知。
     */
    public static ExecutionPathStatus from(String value) {
        for (ExecutionPathStatus item : values()) {
            if (item.value.equals(value)) return item;
        }
        return null;
    }

    public static Map<String, String> toMap() {
        Map<String, String> result = new LinkedHashMap<>();
        for (ExecutionPathStatus item : values()) {
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
