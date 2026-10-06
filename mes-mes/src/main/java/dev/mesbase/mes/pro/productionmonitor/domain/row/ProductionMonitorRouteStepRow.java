package dev.mesbase.mes.pro.productionmonitor.domain.row;

import lombok.Data;

@Data
public class ProductionMonitorRouteStepRow {
    private Integer orderNum;
    private Long processId;
    private String processCode;
    private String processName;
}
