package dev.mesbase.mes.pro.productionmonitor.domain.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ProductionMonitorFlowcardVO {
    private String xtransferNo;
    private Long routeId;
    private String routeName;
    private long reportCount;
    private LocalDateTime firstReportingTime;
    private LocalDateTime lastReportingTime;
    private boolean fullyStored;
}
