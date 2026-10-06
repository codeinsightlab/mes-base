package dev.mesbase.mes.pro.productionmonitor.domain.vo;

import lombok.Data;

@Data
public class ProductionMonitorAggregateCountVO {
    private String code;
    private String label;
    private String category;
    private String level;
    private long count;
}
