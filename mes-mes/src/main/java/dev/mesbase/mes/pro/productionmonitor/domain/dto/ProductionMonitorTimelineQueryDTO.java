package dev.mesbase.mes.pro.productionmonitor.domain.dto;

import lombok.Data;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Data
public class ProductionMonitorTimelineQueryDTO {
    @Min(1) private Integer pageNum = 1;
    @Min(1) @Max(100) private Integer pageSize = 20;
    private String xtransferNo;
    private Long routeId;
    private Long processId;
    private String processCode;
}
