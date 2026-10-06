package dev.mesbase.mes.pro.productionmonitor.domain.row;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProductionMonitorWorkorderRow {
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
    private Long clientId;
    private String clientCode;
    private String clientName;
    private LocalDateTime requestDate;
    private LocalDateTime finishDate;
    private LocalDateTime transactionDate;
    private String status;
}
