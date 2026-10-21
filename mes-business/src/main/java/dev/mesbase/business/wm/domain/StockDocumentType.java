package dev.mesbase.business.wm.domain;

import java.util.Map;
import java.util.LinkedHashMap;

public enum StockDocumentType {
    OTHER_RECEIPT("入库单", "OTHER_RECEIPT"),
    OTHER_ISSUE("出库单", "OTHER_ISSUE");
    private final String description;
    private final String value;

    StockDocumentType(String description, String value) {
        this.description = description;
        this.value = value;
    }

    public String getDescription() {
        return description;
    }

    public String getValue() {
        return value;
    }

    public static StockDocumentType from(String value) {
        for (var item : values()) if (item.value.equals(value)) return item;
        return null;
    }

    public static Map<String, String> toMap() {
        var result = new LinkedHashMap<String, String>();
        for (var item : values()) result.put(item.value, item.description);
        return result;
    }
}
