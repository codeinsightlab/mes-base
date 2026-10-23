package dev.mesbase.business.production.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.LinkedHashMap;
import java.util.Map;

public enum ProductionScanInputMethod {
    TEXT("文本输入", "TEXT"),
    IMAGE("图片识别", "IMAGE");

    private final String description;
    private final String value;

    ProductionScanInputMethod(String description, String value) {
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

    public static ProductionScanInputMethod from(String value) {
        for (var item : values()) if (item.getValue().equals(value)) return item;
        return null;
    }

    public static Map<String, String> toMap() {
        Map<String, String> result = new LinkedHashMap<>();
        for (var item : values()) result.put(item.getValue(), item.getDescription());
        return result;
    }
}
