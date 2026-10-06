package dev.mesbase.mes.pro.productionmonitor.domain.row;

import lombok.Data;

@Data
public class ProductionMonitorStatusCountRow {
    private String statusCode;
    private Long count;
}
