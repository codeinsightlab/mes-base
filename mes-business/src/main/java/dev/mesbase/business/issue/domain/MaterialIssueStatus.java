package dev.mesbase.business.issue.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public enum MaterialIssueStatus {
    DRAFT("草稿", "DRAFT"),
    RECORDED("已记录", "RECORDED"),
    SUBMITTING("提交中", "SUBMITTING"),
    SUBMIT_UNKNOWN("提交待核实", "SUBMIT_UNKNOWN"),
    SUBMIT_REJECTED("提交已拒绝", "SUBMIT_REJECTED"),
    CONFIRMED("已确认", "CONFIRMED"),
    ERP_PENDING("ERP处理中", "ERP_PENDING"),
    ERP_UNKNOWN("ERP待核实", "ERP_UNKNOWN"),
    VOID_PENDING("作废中", "VOID_PENDING"),
    VOID_UNKNOWN("作废待核实", "VOID_UNKNOWN"),
    VOIDED("已作废", "VOIDED");

    private final String description;
    private final String value;

    MaterialIssueStatus(String description, String value) {
        this.description = description;
        this.value = value;
    }

    /**
     * 未知值（包括 null）返回 null，由调用方决定拒绝或保留未知。
     */
    public static MaterialIssueStatus from(String value) {
        for (MaterialIssueStatus item : values()) {
            if (item.value.equals(value)) return item;
        }
        return null;
    }

    public static Map<String, String> toMap() {
        Map<String, String> result = new LinkedHashMap<>();
        for (MaterialIssueStatus item : values()) {
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
