package dev.mesbase.mes.pro.productionmonitor.domain.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ProductionMonitorRouteTrackVO {
    private boolean routeDefinitionMissing;
    private List<ProductionMonitorRouteStepVO> routeDefinition = new ArrayList<>();
    private List<ProductionMonitorObservedProcessVO> observedEvents = new ArrayList<>();
    private List<ProductionMonitorObservedProcessVO> historicalOnlyProcesses = new ArrayList<>();

    @Data
    public static class ProductionMonitorRouteStepVO {
        private Integer orderNum;
        private Long processId;
        private String processCode;
        private String processName;
        private boolean observed;
    }

    @Data
    public static class ProductionMonitorObservedProcessVO {
        private Long processId;
        private String processCode;
        private String processName;
        private LocalDateTime firstReportingTime;
        private LocalDateTime lastReportingTime;
        private Long lastReportingRecordId;
        private long reportCount;
        private BigDecimal reportedQuantity;
        private boolean observed;
        private boolean historicalOnly;
    }
}
