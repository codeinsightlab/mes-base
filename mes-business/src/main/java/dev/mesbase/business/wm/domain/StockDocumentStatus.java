package dev.mesbase.business.wm.domain;

import java.util.Map;
import java.util.LinkedHashMap;

public enum StockDocumentStatus {
    DRAFT("草稿", "DRAFT"),
    POSTED("已审核", "POSTED"),
    CANCELLED("已作废", "CANCELLED");
    private final String description;
    private final String value;

    StockDocumentStatus(String description, String value) {
        this.description = description;
        this.value = value;
    }

    public String getDescription() {
        return description;
    }

    public String getValue() {
        return value;
    }

    public static StockDocumentStatus from(String value) {
        for (var item : values()) if (item.value.equals(value)) return item;
        return null;
    }

    public static Map<String, String> toMap() {
        var result = new LinkedHashMap<String, String>();
        for (var item : values()) result.put(item.value, item.description);
        return result;
    }
}
