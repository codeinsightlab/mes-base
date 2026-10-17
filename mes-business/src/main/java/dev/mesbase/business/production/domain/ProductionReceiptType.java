package dev.mesbase.business.production.domain;

import java.util.Map;
import java.util.LinkedHashMap;

public enum ProductionReceiptType {
    RECEIPT("生产正向交接", "RECEIPT"),
    REVERSAL("原生产交接冲销", "REVERSAL");
    private final String description;
    private final String value;

    ProductionReceiptType(String description, String value) {
        this.description = description;
        this.value = value;
    }

    public static ProductionReceiptType from(String value) {
        for (ProductionReceiptType item : values()) if (item.value.equals(value)) return item;
        return null;
    }

    public static Map<String, String> toMap() {
        Map<String, String> result = new LinkedHashMap<>();
        for (ProductionReceiptType item : values()) result.put(item.value, item.description);
        return result;
    }

    public String getDescription() {
        return description;
    }

    public String getValue() {
        return value;
    }
}
