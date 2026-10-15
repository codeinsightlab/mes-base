package dev.mesbase.business.directory.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public enum DirectoryDeletionFlag {
    NORMAL("未删除", "0"),
    DELETED("已删除", "2");

    private final String description;
    private final String value;

    DirectoryDeletionFlag(String description, String value) {
        this.description = description;
        this.value = value;
    }

    /**
     * 未知值（包括 null）返回 null，由调用方决定拒绝或保留未知。
     */
    public static DirectoryDeletionFlag from(String value) {
        for (DirectoryDeletionFlag item : values()) {
            if (item.value.equals(value)) return item;
        }
        return null;
    }

    public static Map<String, String> toMap() {
        Map<String, String> result = new LinkedHashMap<>();
        for (DirectoryDeletionFlag item : values()) {
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
