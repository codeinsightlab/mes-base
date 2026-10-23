package dev.mesbase.business.basic.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public enum MaterialBomStatus {
    DRAFT("草稿", "DRAFT"), PUBLISHED("已发布", "PUBLISHED"), DISABLED("停用", "DISABLED");
    private final String description;
    private final String value;

    MaterialBomStatus(String description, String value) {
        this.description = description;
        this.value = value;
    }

    public String getDescription() {
        return description;
    }

    public String getValue() {
        return value;
    }

    public static MaterialBomStatus from(String value) {
        for (var s : values()) if (s.value.equals(value)) return s;
        return null;
    }

    public static Map<String, String> toMap() {
        var result = new LinkedHashMap<String, String>();
        for (var s : values()) result.put(s.value, s.description);
        return result;
    }
}
