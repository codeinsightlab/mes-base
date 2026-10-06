package dev.mesbase.mes.pro.productionmonitor.domain.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProductionMonitorTimelineVO {
    private List<ProductionMonitorTimelineEventVO> records;
}
