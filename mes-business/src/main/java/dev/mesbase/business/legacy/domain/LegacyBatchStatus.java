package dev.mesbase.business.legacy.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public enum LegacyBatchStatus {
    PREPARED("准备"), REVIEWED("已审核"), RUNNING("运行中"), COMPLETED("已完成"), FAILED("失败"), CONFLICT("冲突");
    private final String description;

    LegacyBatchStatus(String description) {
        this.description = description;
    }

    public String getValue() {
        return name();
    }

    public String getDescription() {
        return description;
    }

    public static LegacyBatchStatus from(String value) {
        for (var state : values()) if (state.getValue().equals(value)) return state;
        return null;
    }

    public static Map<String, String> toMap() {
        var result = new LinkedHashMap<String, String>();
        for (var state : values()) result.put(state.getValue(), state.getDescription());
        return result;
    }
}
