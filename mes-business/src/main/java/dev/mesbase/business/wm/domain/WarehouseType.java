package dev.mesbase.business.wm.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public enum WarehouseType {
    RAW_MATERIAL("原材料仓", "RAW_MATERIAL"),
    WIP("在制品仓 / 半成品仓", "WIP"),
    FINISHED_GOODS("成品仓", "FINISHED_GOODS"),
    INSPECTION("待检仓", "INSPECTION"),
    DEFECTIVE("不良品仓", "DEFECTIVE"),
    SCRAP("报废品仓", "SCRAP"),
    OUTSOURCING("委外仓", "OUTSOURCING");

    private final String description;
    private final String value;

    WarehouseType(String description, String value) {
        this.description = description;
        this.value = value;
    }

    /**
     * 未知值（包括 null）返回 null，由调用方决定拒绝或保留未知。
     */
    public static WarehouseType from(String value) {
        for (WarehouseType item : values()) {
            if (item.value.equals(value)) return item;
        }
        return null;
    }

    public static Map<String, String> toMap() {
        Map<String, String> result = new LinkedHashMap<>();
        for (WarehouseType item : values()) {
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
