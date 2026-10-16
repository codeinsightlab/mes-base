package dev.mesbase.business.quality.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public enum WasteCategory {
    PROCESS("工废", "PROCESS"), MATERIAL("料废", "MATERIAL");
    private final String description;
    private final String value;

    WasteCategory(String description, String value) {
        this.description = description;
        this.value = value;
    }

    public String getDescription() {
        return description;
    }

    public String getValue() {
        return value;
    }

    public static WasteCategory from(String value) {
        for (var item : values()) if (item.value.equals(value)) return item;
        return null;
    }

    public static Map<String, String> toMap() {
        var result = new LinkedHashMap<String, String>();
        for (var item : values()) result.put(item.value, item.description);
        return result;
    }
}
