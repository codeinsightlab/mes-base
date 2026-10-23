package dev.mesbase.business.production.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.LinkedHashMap;
import java.util.Map;

public enum ProductionScanType {
    EXECUTION_LOOKUP("执行定位", "EXECUTION_LOOKUP", "business:mes:production:execution:query"),
    REPORTING("生产报工", "REPORTING", "business:production:report:submit"),
    DEFECT_DECLARATION("不良申报", "DEFECT_DECLARATION", "business:quality:defect:create");

    private final String description;
    private final String value;
    private final String permission;

    ProductionScanType(String description, String value, String permission) {
        this.description = description;
        this.value = value;
        this.permission = permission;
    }

    public String getDescription() {
        return description;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public String getPermission() {
        return permission;
    }

    public static ProductionScanType from(String value) {
        for (var item : values()) if (item.getValue().equals(value)) return item;
        return null;
    }

    public static Map<String, String> toMap() {
        Map<String, String> result = new LinkedHashMap<>();
        for (var item : values()) result.put(item.getValue(), item.getDescription());
        return result;
    }
}
