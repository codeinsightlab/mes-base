package dev.mesbase.mes.pro.productionmonitor.domain.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ProductionMonitorDetailVO {
    private Long workorderId;
    private String workorderCode;
    private String workorderName;
    private String orderSource;
    private String sourceCode;
    private Long productId;
    private String productCode;
    private String productName;
    private String productSpc;
    private String unitOfMeasure;
    private BigDecimal quantity;
    private BigDecimal quantityProduced;
    private BigDecimal completionRatio;
    private Long clientId;
    private String clientCode;
    private String clientName;
    private LocalDateTime requestDate;
    private LocalDateTime finishDate;
    private LocalDateTime transactionDate;
    private String status;
    private String statusName;
    private String reportingCoverage;
    private long reportCount;
    private long flowcardCount;
    private LocalDateTime lastReportingTime;
    private Long lastProcessId;
    private String lastProcessCode;
    private String lastProcessName;
    private Long lastUserId;
    private String lastUserName;
    private String lastNickName;
    private long defectiveRecordCount;
    private long fullyStoredFlowcardCount;
    private List<ProductionMonitorRiskTag> riskTags = new ArrayList<>();
    private List<ProductionMonitorFlowcardVO> flowcards = new ArrayList<>();
}
