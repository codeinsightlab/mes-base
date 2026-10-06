package dev.mesbase.mes.pro.productionmonitor.domain.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProductionMonitorTimelineEventVO {
    private Long recordId;
    private LocalDateTime createTime;
    private String xtransferNo;
    private Long routeId;
    private String routeName;
    private Long processId;
    private String processCode;
    private String processName;
    private String unitOfMeasure;
    private Long userId;
    private String userName;
    private String nickName;
    private BigDecimal reportingWorkQuantity;
    private String status;
    private String statusName;
}
