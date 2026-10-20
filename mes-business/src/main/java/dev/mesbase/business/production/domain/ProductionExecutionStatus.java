package dev.mesbase.business.production.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public enum ProductionExecutionStatus {
    HISTORICAL("历史只读", "HISTORICAL"),
    READY("待执行", "READY"),
    RUNNING("执行中", "RUNNING"),
    COMPLETED("已结束", "COMPLETED"),
    CANCELLED("已取消", "CANCELLED");

    private final String description;
    private final String value;

    ProductionExecutionStatus(String description, String value) {
        this.description = description;
        this.value = value;
    }

    /**
     * 未知值（包括 null）返回 null，由调用方决定拒绝或保留未知。
     */
    public static ProductionExecutionStatus from(String value) {
        for (ProductionExecutionStatus item : values()) {
            if (item.value.equals(value)) return item;
        }
        return null;
    }

    public static Map<String, String> toMap() {
        Map<String, String> result = new LinkedHashMap<>();
        for (ProductionExecutionStatus item : values()) {
            result.put(item.value, item.description);
        }
        return result;
    }

    /**
     * Normal forward reporting accepts READY and RUNNING; historical corrections have separate gates.
     */
    public static boolean allowsNormalReporting(String value) {
        return from(value) == READY || from(value) == RUNNING;
    }

    public String getDescription() {
        return description;
    }

    public String getValue() {
        return value;
    }
}
