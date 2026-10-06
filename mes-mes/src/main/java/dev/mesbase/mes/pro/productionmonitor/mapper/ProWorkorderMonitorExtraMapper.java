package dev.mesbase.mes.pro.productionmonitor.mapper;

import dev.mesbase.mes.pro.productionmonitor.domain.dto.ProductionMonitorQueryDTO;
import dev.mesbase.mes.pro.productionmonitor.domain.row.ProductionMonitorStatusCountRow;
import dev.mesbase.mes.pro.productionmonitor.domain.row.ProductionMonitorAggregateCountRow;
import dev.mesbase.mes.pro.productionmonitor.domain.row.ProductionMonitorSummaryRow;
import dev.mesbase.mes.pro.productionmonitor.domain.row.ProductionMonitorRouteStepRow;
import dev.mesbase.mes.pro.productionmonitor.domain.row.ProductionMonitorWorkorderRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProWorkorderMonitorExtraMapper {
    ProductionMonitorSummaryRow selectSummary(@Param("query") ProductionMonitorQueryDTO query,
                                               @Param("today") LocalDate today,
                                               @Param("now") LocalDateTime now,
                                               @Param("terminalStatusCodes") List<String> terminalStatusCodes,
                                               @Param("productionStatusCodes") List<String> productionStatusCodes);
    List<ProductionMonitorStatusCountRow> selectSummaryStatusCounts(@Param("query") ProductionMonitorQueryDTO query);
    List<ProductionMonitorAggregateCountRow> selectSummaryCompletionBuckets(@Param("query") ProductionMonitorQueryDTO query);
    List<ProductionMonitorAggregateCountRow> selectSummaryAlertCounts(@Param("query") ProductionMonitorQueryDTO query,
                                                                       @Param("today") LocalDate today,
                                                                       @Param("now") LocalDateTime now,
                                                                       @Param("terminalStatusCodes") List<String> terminalStatusCodes,
                                                                       @Param("productionStatusCodes") List<String> productionStatusCodes);
    long countAttentionWorkorders(@Param("query") ProductionMonitorQueryDTO query,
                                  @Param("today") LocalDate today,
                                  @Param("now") LocalDateTime now,
                                  @Param("terminalStatusCodes") List<String> terminalStatusCodes,
                                  @Param("productionStatusCodes") List<String> productionStatusCodes);
    List<ProductionMonitorWorkorderRow> selectWorkorders(@Param("query") ProductionMonitorQueryDTO query,
                                                         @Param("today") LocalDate today,
                                                         @Param("now") LocalDateTime now,
                                                         @Param("terminalStatusCodes") List<String> terminalStatusCodes,
                                                         @Param("productionStatusCodes") List<String> productionStatusCodes);
    ProductionMonitorWorkorderRow selectWorkorderById(@Param("workorderId") Long workorderId);
    List<ProductionMonitorRouteStepRow> selectRouteDefinition(@Param("routeId") Long routeId);
}
