package dev.mesbase.mes.pro.productionmonitor.service;

import dev.mesbase.mes.pro.productionmonitor.domain.dto.ProductionMonitorQueryDTO;
import dev.mesbase.mes.pro.productionmonitor.domain.dto.ProductionMonitorTimelineQueryDTO;
import dev.mesbase.mes.pro.productionmonitor.domain.vo.ProductionMonitorDetailVO;
import dev.mesbase.mes.pro.productionmonitor.domain.vo.ProductionMonitorRouteTrackVO;
import dev.mesbase.mes.pro.productionmonitor.domain.vo.ProductionMonitorSummaryVO;
import dev.mesbase.mes.pro.productionmonitor.domain.vo.ProductionMonitorTimelineEventVO;
import dev.mesbase.mes.pro.productionmonitor.domain.vo.ProductionMonitorWorkorderVO;
import java.util.List;

public interface ProductionMonitorService {
    ProductionMonitorSummaryVO summary(String month);
    List<ProductionMonitorWorkorderVO> workorders(ProductionMonitorQueryDTO query);
    ProductionMonitorDetailVO detail(Long workorderId);
    List<ProductionMonitorTimelineEventVO> timeline(Long workorderId, ProductionMonitorTimelineQueryDTO query);
    ProductionMonitorRouteTrackVO routeTrack(Long workorderId, String xtransferNo, Long routeId);
}
