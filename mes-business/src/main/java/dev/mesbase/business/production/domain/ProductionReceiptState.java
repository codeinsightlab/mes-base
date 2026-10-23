package dev.mesbase.business.production.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Read-only progress of normal and quality inbound closure.
 */
public enum ProductionReceiptState {
    PENDING("待入库", "PENDING"),
    PARTIAL("部分入库", "PARTIAL"),
    FULLY_RECEIVED("完全入库", "FULLY_RECEIVED");

    private final String description;
    private final String value;

    ProductionReceiptState(String description, String value) {
        this.description = description;
        this.value = value;
    }

    public static ProductionReceiptState from(String value) {
        for (var state : values()) if (state.value.equals(value)) return state;
        return null;
    }

    public static Map<String, String> toMap() {
        Map<String, String> states = new LinkedHashMap<>();
        for (var state : values()) states.put(state.value, state.description);
        return states;
    }

    public String getDescription() {
        return description;
    }

    public String getValue() {
        return value;
    }
}
