package dev.mesbase.mes.pro.productionmonitor.domain.row;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ProductionMonitorFlowcardRow {
    private String xtransferNo;
    private Long routeId;
    private String routeName;
    private Long reportCount;
    private LocalDateTime firstReportingTime;
    private LocalDateTime lastReportingTime;
    private boolean fullyStored;
}
