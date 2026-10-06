package dev.mesbase.mes.pro.productionmonitor.domain.dto;

import lombok.Data;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;

@Data
public class ProductionMonitorQueryDTO {
    private String month;
    private String status;
    private String workorderCode;
    private String productCode;
    private String coverage;
    private String riskTag;
    private Boolean attentionOnly;
    private Boolean inProductionOnly;
    private LocalDate finishDateFrom;
    private LocalDate finishDateTo;
    @Min(1) private Integer pageNum = 1;
    @Min(1) @Max(100) private Integer pageSize = 20;
    private java.time.LocalDateTime monthStart;
    private java.time.LocalDateTime nextMonthStart;
}
