package dev.mesbase.mes.pro.productionmonitor.domain.row;

import lombok.Data;

@Data
public class ProductionMonitorSummaryRow {
    private Long totalWorkorders;
    private Long reportingCoveredCount;
    private Long reportingMissingCount;
    private Long planDatePassedCount;
    private Long nearPlanDateLowCompletionCount;
    private Long reportSilenceCount;
    private Long defectiveWorkorderCount;
}
