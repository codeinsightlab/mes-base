package dev.mesbase.mes.pro.productionmonitor.domain.row;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ProductionMonitorReportingAggregateRow {
    private String workorderCode;
    private Long reportCount;
    private Long flowcardCount;
    private Long fullyStoredFlowcardCount;
    private LocalDateTime firstReportingTime;
    private LocalDateTime lastReportingTime;
    private Long lastProcessId;
    private String lastProcessCode;
    private String lastProcessName;
    private Long lastUserId;
    private String lastUserName;
    private String lastNickName;
}
