package dev.mesbase.business.quality.domain;

import java.util.Map;
import java.util.LinkedHashMap;

public enum QualityReleaseAction {
    RELEASE("累计数量正式放行", "RELEASE"),
    REVOKE("撤回原放行", "REVOKE");
    private final String description;
    private final String value;

    QualityReleaseAction(String description, String value) {
        this.description = description;
        this.value = value;
    }

    public static QualityReleaseAction from(String value) {
        for (QualityReleaseAction item : values()) if (item.value.equals(value)) return item;
        return null;
    }

    public static Map<String, String> toMap() {
        Map<String, String> result = new LinkedHashMap<>();
        for (QualityReleaseAction item : values()) result.put(item.value, item.description);
        return result;
    }

    public String getDescription() {
        return description;
    }

    public String getValue() {
        return value;
    }
}
