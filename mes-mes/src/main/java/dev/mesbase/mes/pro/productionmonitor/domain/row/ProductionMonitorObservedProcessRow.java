package dev.mesbase.mes.pro.productionmonitor.domain.row;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProductionMonitorObservedProcessRow {
    private Long processId;
    private String processCode;
    private String processName;
    private LocalDateTime firstReportingTime;
    private LocalDateTime lastReportingTime;
    private Long lastReportingRecordId;
    private Long reportCount;
    private BigDecimal reportedQuantity;
}
