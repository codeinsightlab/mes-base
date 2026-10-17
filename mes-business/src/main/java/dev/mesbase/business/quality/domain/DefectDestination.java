package dev.mesbase.business.quality.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public enum DefectDestination {
    SAME_NODE("所属工序处置", "SAME_NODE"), ROUTING_NODE("退指定工序", "ROUTING_NODE"), WAREHOUSE("退指定仓库", "WAREHOUSE");
    private final String description;
    private final String value;

    DefectDestination(String description, String value) {
        this.description = description;
        this.value = value;
    }

    public String getDescription() {
        return description;
    }

    public String getValue() {
        return value;
    }

    public static DefectDestination from(String value) {
        for (var item : values()) if (item.value.equals(value)) return item;
        return null;
    }

    public static Map<String, String> toMap() {
        var result = new LinkedHashMap<String, String>();
        for (var item : values()) result.put(item.value, item.description);
        return result;
    }
}
