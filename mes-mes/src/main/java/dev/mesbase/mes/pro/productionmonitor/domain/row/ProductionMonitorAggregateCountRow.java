package dev.mesbase.mes.pro.productionmonitor.domain.row;

import lombok.Data;

@Data
public class ProductionMonitorAggregateCountRow {
    private String code;
    private String label;
    private String category;
    private Long count;
}
