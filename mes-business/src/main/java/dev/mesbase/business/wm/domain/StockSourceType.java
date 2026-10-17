package dev.mesbase.business.wm.domain;

import java.util.Map;
import java.util.LinkedHashMap;

public enum StockSourceType {
    INVENTORY("库存业务", "INVENTORY"),
    PRODUCTION_RECEIPT("生产交接", "PRODUCTION_RECEIPT"),
    MATERIAL_ISSUE("生产领料", "MATERIAL_ISSUE"),
    OTHER_RECEIPT("其他入库", "OTHER_RECEIPT"),
    OTHER_ISSUE("其他出库", "OTHER_ISSUE");
    private final String description;
    private final String value;

    StockSourceType(String description, String value) {
        this.description = description;
        this.value = value;
    }

    public static StockSourceType from(String value) {
        for (StockSourceType item : values()) if (item.value.equals(value)) return item;
        return null;
    }

    public static Map<String, String> toMap() {
        Map<String, String> result = new LinkedHashMap<>();
        for (StockSourceType item : values()) result.put(item.value, item.description);
        return result;
    }

    public String getDescription() {
        return description;
    }

    public String getValue() {
        return value;
    }
}
