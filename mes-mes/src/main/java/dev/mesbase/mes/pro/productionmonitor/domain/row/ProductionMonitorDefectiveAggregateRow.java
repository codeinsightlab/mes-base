package dev.mesbase.mes.pro.productionmonitor.domain.row;

import lombok.Data;

@Data
public class ProductionMonitorDefectiveAggregateRow {
    private String workorderCode;
    private Long defectiveRecordCount;
}
