package dev.mesbase.business.quality.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public enum DefectRecordAction {
    CREATE("申报", "CREATE"), REVISE("订正", "REVISE"), VOID("作废", "VOID");
    private final String description;
    private final String value;

    DefectRecordAction(String description, String value) {
        this.description = description;
        this.value = value;
    }

    public String getDescription() {
        return description;
    }

    public String getValue() {
        return value;
    }

    public static DefectRecordAction from(String value) {
        for (var item : values()) if (item.value.equals(value)) return item;
        return null;
    }

    public static Map<String, String> toMap() {
        var result = new LinkedHashMap<String, String>();
        for (var item : values()) result.put(item.value, item.description);
        return result;
    }
}
