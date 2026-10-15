package dev.mesbase.business.basic.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public enum MaterialType {
    ITEM("物料", "ITEM"),
    PRODUCT("产品", "PRODUCT");

    private final String description;
    private final String value;

    MaterialType(String description, String value) {
        this.description = description;
        this.value = value;
    }

    /**
     * 未知值（包括 null）返回 null，由调用方决定拒绝或保留未知。
     */
    public static MaterialType from(String value) {
        for (MaterialType item : values()) {
            if (item.value.equals(value)) return item;
        }
        return null;
    }

    public static Map<String, String> toMap() {
        Map<String, String> result = new LinkedHashMap<>();
        for (MaterialType item : values()) {
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
