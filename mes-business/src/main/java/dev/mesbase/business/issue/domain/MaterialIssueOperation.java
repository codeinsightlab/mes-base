package dev.mesbase.business.issue.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public enum MaterialIssueOperation {
    LOCAL_RECORD("本地记录", "LOCAL_RECORD"),
    SUBMIT("提交", "SUBMIT"),
    VOID("作废", "VOID"),
    RECONCILE("核实", "RECONCILE"),
    REOPEN_REJECTED("拒绝后重新编辑", "REOPEN_REJECTED");

    private final String description;
    private final String value;

    MaterialIssueOperation(String description, String value) {
        this.description = description;
        this.value = value;
    }

    /**
     * 未知值（包括 null）返回 null，由调用方决定拒绝或保留未知。
     */
    public static MaterialIssueOperation from(String value) {
        for (MaterialIssueOperation item : values()) {
            if (item.value.equals(value)) return item;
        }
        return null;
    }

    public static Map<String, String> toMap() {
        Map<String, String> result = new LinkedHashMap<>();
        for (MaterialIssueOperation item : values()) {
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
