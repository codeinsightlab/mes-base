package dev.mesbase.mes.pro.productionmonitor.domain.vo;

import lombok.Data;

@Data
public class ProductionMonitorStatusCountVO {
    private String statusCode;
    private String statusName;
    private long count;
}
