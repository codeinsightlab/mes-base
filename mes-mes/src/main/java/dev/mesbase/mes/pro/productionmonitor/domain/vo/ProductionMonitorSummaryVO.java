package dev.mesbase.mes.pro.productionmonitor.domain.vo;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class ProductionMonitorSummaryVO {
    private String month;
    private long totalWorkorders;
    private List<ProductionMonitorStatusCountVO> statusCounts = new ArrayList<>();
    private List<ProductionMonitorAggregateCountVO> completionBuckets = new ArrayList<>();
    private List<ProductionMonitorAggregateCountVO> alertCounts = new ArrayList<>();
    private long attentionWorkorderCount;
    private long inProductionCount;
    private long reportingCoveredCount;
    private long reportingMissingCount;
    private long planDatePassedCount;
    private long nearPlanDateLowCompletionCount;
    private long reportSilenceCount;
    private long defectiveWorkorderCount;
}
