package dev.mesbase.business.issue.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public enum MaterialIssueSubmissionStatus {
    SUBMITTING("提交中", "SUBMITTING"),
    SUCCEEDED("成功", "SUCCEEDED"),
    REJECTED("已拒绝", "REJECTED"),
    UNKNOWN("待核实", "UNKNOWN");

    private final String description;
    private final String value;

    MaterialIssueSubmissionStatus(String description, String value) {
        this.description = description;
        this.value = value;
    }

    /**
     * 未知值（包括 null）返回 null，由调用方决定拒绝或保留未知。
     */
    public static MaterialIssueSubmissionStatus from(String value) {
        for (MaterialIssueSubmissionStatus item : values()) {
            if (item.value.equals(value)) return item;
        }
        return null;
    }

    public static Map<String, String> toMap() {
        Map<String, String> result = new LinkedHashMap<>();
        for (MaterialIssueSubmissionStatus item : values()) {
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
