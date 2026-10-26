package dev.mesbase.business.production.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public enum ProductionPlanStatus {
    DRAFT("草稿", "DRAFT");

    private final String description;
    private final String value;

    ProductionPlanStatus(String description, String value) {
        this.description = description;
        this.value = value;
    }

    public String getDescription() {
        return description;
    }

    public String getValue() {
        return value;
    }

    public static ProductionPlanStatus from(String value) {
        for (var status : values()) {
            if (status.value.equals(value)) return status;
        }
        return null;
    }

    public static Map<String, String> toMap() {
        Map<String, String> options = new LinkedHashMap<>();
        for (var status : values()) options.put(status.value, status.description);
        return options;
    }
}
