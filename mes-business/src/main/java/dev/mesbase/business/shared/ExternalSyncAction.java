package dev.mesbase.business.shared;

import java.util.LinkedHashMap;
import java.util.Map;

public enum ExternalSyncAction {
    CREATE("新增", "CREATE"), UPDATE("更新", "UPDATE"), UNCHANGED("无变化", "UNCHANGED"),
    CONFLICT("冲突", "CONFLICT"), MAPPING_REQUIRED("需要映射", "MAPPING_REQUIRED"),
    IDENTITY_UNVERIFIED("身份未确认", "IDENTITY_UNVERIFIED"), STATUS_UNVERIFIED("状态未确认", "STATUS_UNVERIFIED"),
    UNSUPPORTED("不支持", "UNSUPPORTED");
    private final String description;
    private final String value;

    ExternalSyncAction(String description, String value) {
        this.description = description;
        this.value = value;
    }

    public String getDescription() {
        return description;
    }

    public String getValue() {
        return value;
    }

    public static ExternalSyncAction from(String value) {
        for (var action : values()) if (action.value.equals(value)) return action;
        return null;
    }

    public static Map<String, String> toMap() {
        var result = new LinkedHashMap<String, String>();
        for (var action : values()) result.put(action.value, action.description);
        return result;
    }
}
