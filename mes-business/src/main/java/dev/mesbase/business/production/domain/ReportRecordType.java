package dev.mesbase.business.production.domain;

public enum ReportRecordType {
    REPORT("原始报工", "REPORT"), ADJUSTMENT("数量调整", "ADJUSTMENT");
    private final String description;
    private final String value;

    ReportRecordType(String description, String value) {
        this.description = description;
        this.value = value;
    }

    public String getDescription() {
        return description;
    }

    public String getValue() {
        return value;
    }

    public static ReportRecordType from(String value) {
        for (var type : values()) if (type.value.equals(value)) return type;
        return null;
    }
}
