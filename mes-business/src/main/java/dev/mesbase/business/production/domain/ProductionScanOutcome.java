package dev.mesbase.business.production.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.LinkedHashMap;
import java.util.Map;

public enum ProductionScanOutcome {
    SUCCESS("定位成功", "SUCCESS"),
    FAILURE("定位失败", "FAILURE");

    private final String description;
    private final String value;

    ProductionScanOutcome(String description, String value) {
        this.description = description;
        this.value = value;
    }

    public String getDescription() {
        return description;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static ProductionScanOutcome from(String value) {
        for (var item : values()) if (item.getValue().equals(value)) return item;
        return null;
    }

    public static Map<String, String> toMap() {
        Map<String, String> result = new LinkedHashMap<>();
        for (var item : values()) result.put(item.getValue(), item.getDescription());
        return result;
    }
}
