package dev.mesbase.business.production.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public enum WorkOrderStatus {
    DRAFT("草稿", "DRAFT"),
    RELEASED("已下达", "RELEASED"),
    CANCELLED("已作废", "CANCELLED");

    private final String description;
    private final String value;

    WorkOrderStatus(String description, String value) {
        this.description = description;
        this.value = value;
    }

    /**
     * 未知值（包括 null）返回 null，由调用方决定拒绝或保留未知。
     */
    public static WorkOrderStatus from(String value) {
        for (WorkOrderStatus item : values()) {
            if (item.value.equals(value)) return item;
        }
        return null;
    }

    public static Map<String, String> toMap() {
        Map<String, String> result = new LinkedHashMap<>();
        for (WorkOrderStatus item : values()) {
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
