package dev.mesbase.mes.pro.productionmonitor.domain.query;

import lombok.Data;

@Data
public class ProductionMonitorTimelineQuery {
    private String workorderCode;
    private String xtransferNo;
    private Long routeId;
    private Long processId;
    private String processCode;
}
