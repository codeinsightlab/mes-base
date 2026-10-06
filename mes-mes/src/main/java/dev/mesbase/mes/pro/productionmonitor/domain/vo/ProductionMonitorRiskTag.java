package dev.mesbase.mes.pro.productionmonitor.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum ProductionMonitorRiskTag {
    PLAN_DATE_PASSED("计划日期已过", "WARNING"),
    NEAR_PLAN_DATE("计划日临近", "INFO"),
    LOW_STORED_COMPLETION("生产进度偏低", "WARNING"),
    REPORTING_SILENCE("最近72小时无新报工记录", "WARNING"),
    NO_REPORTING_RECORD("未发现本报工表关联记录", "INFO"),
    HAS_DEFECTIVE_RECORD("存在不良记录", "WARNING");

    private final String label;
    private final String level;
    ProductionMonitorRiskTag(String label, String level) { this.label = label; this.level = level; }
    public String getCode() { return name(); }
    public String getLabel() { return label; }
    public String getLevel() { return level; }
}
