package dev.mesbase.mes.pro.productionmonitor.mapper;

import dev.mesbase.mes.pro.productionmonitor.domain.row.ProductionMonitorFlowcardRow;
import dev.mesbase.mes.pro.productionmonitor.domain.row.ProductionMonitorObservedProcessRow;
import dev.mesbase.mes.pro.productionmonitor.domain.row.ProductionMonitorReportingAggregateRow;
import dev.mesbase.mes.pro.productionmonitor.domain.row.ProductionMonitorTimelineRow;
import dev.mesbase.mes.pro.productionmonitor.domain.query.ProductionMonitorTimelineQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ProReportingWorkMonitorExtraMapper {
    List<ProductionMonitorReportingAggregateRow> selectWorkorderAggregates(@Param("workorderCodes") List<String> workorderCodes,
                                                                           @Param("fullyStoredStatus") String fullyStoredStatus);
    List<ProductionMonitorReportingAggregateRow> selectLatestByWorkorderCodes(@Param("workorderCodes") List<String> workorderCodes);
    List<ProductionMonitorFlowcardRow> selectFlowcardsByWorkorderCode(@Param("workorderCode") String workorderCode,
                                                                       @Param("fullyStoredStatus") String fullyStoredStatus);
    List<ProductionMonitorObservedProcessRow> selectObservedProcesses(@Param("workorderCode") String workorderCode,
                                                                      @Param("xtransferNo") String xtransferNo,
                                                                      @Param("routeId") Long routeId);
    List<ProductionMonitorTimelineRow> selectTimeline(@Param("query") ProductionMonitorTimelineQuery query);
}
