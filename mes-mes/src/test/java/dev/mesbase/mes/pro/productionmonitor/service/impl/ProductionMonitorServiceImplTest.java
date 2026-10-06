package dev.mesbase.mes.pro.productionmonitor.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.common.core.domain.entity.SysDictData;
import dev.mesbase.common.core.redis.RedisCache;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.common.utils.DictUtils;
import dev.mesbase.mes.pro.productionmonitor.domain.dto.ProductionMonitorQueryDTO;
import dev.mesbase.mes.pro.productionmonitor.domain.dto.ProductionMonitorTimelineQueryDTO;
import dev.mesbase.mes.pro.productionmonitor.domain.query.ProductionMonitorTimelineQuery;
import dev.mesbase.mes.pro.productionmonitor.domain.row.*;
import dev.mesbase.mes.pro.productionmonitor.domain.vo.*;
import dev.mesbase.mes.pro.productionmonitor.mapper.ProReportingWorkMonitorExtraMapper;
import dev.mesbase.mes.pro.productionmonitor.mapper.ProWorkorderMonitorExtraMapper;
import dev.mesbase.mes.pro.productionmonitor.mapper.QcDefectiveMonitorExtraMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductionMonitorServiceImplTest {
    private final ProWorkorderMonitorExtraMapper workorders = mock(ProWorkorderMonitorExtraMapper.class);
    private final ProReportingWorkMonitorExtraMapper reporting = mock(ProReportingWorkMonitorExtraMapper.class);
    private final QcDefectiveMonitorExtraMapper defective = mock(QcDefectiveMonitorExtraMapper.class);
    private final RedisCache redisCache = mock(RedisCache.class);
    private ProductionMonitorServiceImpl service;

    @BeforeEach
    void setUp() {
        FactoryContext.bind(new dev.mesbase.core.factory.FactoryScope(new dev.mesbase.core.factory.FactoryId("1")));
        when(redisCache.getCacheObject(DictUtils.getCacheKey("mes_order_status"))).thenReturn(workorderStatuses());
        when(redisCache.getCacheObject(DictUtils.getCacheKey("mes_feedback_status"))).thenReturn(reportStatuses());
        when(reporting.selectWorkorderAggregates(anyList(), anyString())).thenReturn(Collections.emptyList());
        when(reporting.selectLatestByWorkorderCodes(anyList())).thenReturn(Collections.emptyList());
        when(reporting.selectFlowcardsByWorkorderCode(anyString(), anyString())).thenReturn(Collections.emptyList());
        when(defective.selectWorkorderAggregates(anyList())).thenReturn(Collections.emptyList());
        service = new ProductionMonitorServiceImpl(workorders, reporting, defective, redisCache);
    }

    @AfterEach
    void clearFactory() { PageHelper.clearPage(); FactoryContext.clear(); }

    @Test
    void timelineValidatesWorkorderBeforePagingMapperAndPreservesPageMetadata() {
        ProductionMonitorWorkorderRow workorder = workorder("WO-51", BigDecimal.TEN, BigDecimal.ONE);
        when(workorders.selectWorkorderById(51L)).thenReturn(workorder);
        Page<ProductionMonitorTimelineRow> source = new Page<>(2, 30);
        source.setTotal(51L);
        for (long recordId = 31; recordId <= 51; recordId++) {
            ProductionMonitorTimelineRow row = new ProductionMonitorTimelineRow();
            row.setRecordId(recordId);
            row.setCreateTime(LocalDateTime.of(2026, 9, 29, 14, 30).minusMinutes(recordId));
            row.setReportingWorkQuantity(BigDecimal.ONE);
            row.setUnitOfMeasure("件");
            source.add(row);
        }
        when(reporting.selectTimeline(any(ProductionMonitorTimelineQuery.class))).thenReturn(source);

        ProductionMonitorTimelineQueryDTO query = new ProductionMonitorTimelineQueryDTO();
        query.setPageNum(2);
        query.setPageSize(30);
        query.setXtransferNo("FC-51");
        query.setRouteId(7L);
        query.setProcessId(8L);
        query.setProcessCode("P-8");

        List<ProductionMonitorTimelineEventVO> result = service.timeline(51L, query);

        assertEquals(21, result.size());
        assertTrue(result instanceof Page);
        assertEquals(51L, ((Page<?>) result).getTotal());
        assertEquals(2, ((Page<?>) result).getPageNum());
        assertEquals(30, ((Page<?>) result).getPageSize());
        assertEquals("件", result.get(0).getUnitOfMeasure());
        org.mockito.InOrder order = inOrder(workorders, reporting);
        order.verify(workorders).selectWorkorderById(51L);
        order.verify(reporting).selectTimeline(any(ProductionMonitorTimelineQuery.class));
        ArgumentCaptor<ProductionMonitorTimelineQuery> captured = ArgumentCaptor.forClass(ProductionMonitorTimelineQuery.class);
        verify(reporting).selectTimeline(captured.capture());
        assertEquals("WO-51", captured.getValue().getWorkorderCode());
        assertEquals("FC-51", captured.getValue().getXtransferNo());
        assertEquals(7L, captured.getValue().getRouteId());
        assertEquals(8L, captured.getValue().getProcessId());
        assertEquals("P-8", captured.getValue().getProcessCode());
    }

    @Test
    void summaryUsesMonthAggregateAndDictionaryNames() {
        ProductionMonitorSummaryRow aggregate = new ProductionMonitorSummaryRow();
        aggregate.setTotalWorkorders(2L);
        aggregate.setReportingCoveredCount(1L);
        aggregate.setReportingMissingCount(1L);
        aggregate.setPlanDatePassedCount(1L);
        aggregate.setNearPlanDateLowCompletionCount(1L);
        aggregate.setReportSilenceCount(1L);
        aggregate.setDefectiveWorkorderCount(1L);
        when(workorders.selectSummary(any(), any(), any(), anyList(), anyList())).thenReturn(aggregate);
        ProductionMonitorStatusCountRow status = new ProductionMonitorStatusCountRow();
        status.setStatusCode("3");
        status.setCount(2L);
        when(workorders.selectSummaryStatusCounts(any())).thenReturn(Collections.singletonList(status));
        when(workorders.countAttentionWorkorders(any(), any(), any(), anyList(), anyList())).thenReturn(1L);
        ProductionMonitorAggregateCountRow completion = new ProductionMonitorAggregateCountRow();
        completion.setLabel("0%");
        completion.setCode("0%");
        completion.setCategory("COMPLETION");
        completion.setCount(2L);
        when(workorders.selectSummaryCompletionBuckets(any())).thenReturn(Collections.singletonList(completion));
        ProductionMonitorAggregateCountRow alert = new ProductionMonitorAggregateCountRow();
        alert.setLabel("存在不良记录");
        alert.setCode("HAS_DEFECTIVE_RECORD");
        alert.setCategory("PRODUCTION");
        alert.setCount(1L);
        when(workorders.selectSummaryAlertCounts(any(), any(), any(), anyList(), anyList())).thenReturn(Collections.singletonList(alert));

        ProductionMonitorSummaryVO result = service.summary("2026-09");

        assertEquals("2026-09", result.getMonth());
        assertEquals(2L, result.getTotalWorkorders());
        assertEquals("生产中", result.getStatusCounts().get(0).getStatusName());
        assertEquals(1L, result.getReportingMissingCount());
        assertEquals(2L, result.getInProductionCount());
        assertEquals(1L, result.getAttentionWorkorderCount());
        assertEquals("0%", result.getCompletionBuckets().get(0).getLabel());
        assertEquals("danger", result.getAlertCounts().get(0).getLevel());
    }

    @Test
    void workorderPageUsesFixedBatchReadsAndKeepsPageTotal() {
        ProductionMonitorWorkorderRow row = workorder("WO-1", new BigDecimal("100"), new BigDecimal("24"));
        row.setStatus("3");
        row.setFinishDate(LocalDateTime.now().plusDays(2));
        Page<ProductionMonitorWorkorderRow> page = new Page<>(2, 10);
        page.setTotal(21L);
        page.add(row);
        when(workorders.selectWorkorders(any(), any(), any(), anyList(), anyList())).thenReturn(page);
        ProductionMonitorReportingAggregateRow agg = new ProductionMonitorReportingAggregateRow();
        agg.setWorkorderCode("WO-1");
        agg.setReportCount(2L);
        agg.setFlowcardCount(2L);
        agg.setFullyStoredFlowcardCount(1L);
        agg.setLastReportingTime(LocalDateTime.now().minusHours(73));
        when(reporting.selectWorkorderAggregates(anyList(), anyString())).thenReturn(Collections.singletonList(agg));
        ProductionMonitorReportingAggregateRow latest = new ProductionMonitorReportingAggregateRow();
        latest.setWorkorderCode("WO-1");
        latest.setLastReportingTime(agg.getLastReportingTime());
        latest.setLastProcessCode("P-2");
        when(reporting.selectLatestByWorkorderCodes(anyList())).thenReturn(Collections.singletonList(latest));

        ProductionMonitorQueryDTO query = new ProductionMonitorQueryDTO();
        query.setMonth("2026-09");
        query.setPageNum(2);
        query.setPageSize(10);
        List<ProductionMonitorWorkorderVO> result = service.workorders(query);

        assertEquals(1, result.size());
        assertEquals(21L, ((Page<?>) result).getTotal());
        assertEquals("HAS_RECORD", result.get(0).getReportingCoverage());
        assertEquals(2L, result.get(0).getFlowcardCount());
        assertEquals(1L, result.get(0).getFullyStoredFlowcardCount());
        assertEquals(new BigDecimal("0.240000"), result.get(0).getCompletionRatio());
        assertTrue(result.get(0).getRiskTags().contains(ProductionMonitorRiskTag.NEAR_PLAN_DATE));
        assertTrue(result.get(0).getRiskTags().contains(ProductionMonitorRiskTag.LOW_STORED_COMPLETION));
        assertTrue(result.get(0).getRiskTags().contains(ProductionMonitorRiskTag.REPORTING_SILENCE));
        verify(reporting, times(1)).selectWorkorderAggregates(anyList(), anyString());
        verify(reporting, times(1)).selectLatestByWorkorderCodes(anyList());
        verify(defective, times(1)).selectWorkorderAggregates(anyList());
    }

    @Test
    void noReportAndZeroOrNullQuantityAreExplicit() {
        ProductionMonitorWorkorderRow noReport = workorder("WO-2", BigDecimal.ZERO, BigDecimal.TEN);
        noReport.setStatus("3");
        ProductionMonitorWorkorderRow nullQuantity = workorder("WO-3", null, BigDecimal.TEN);
        Page<ProductionMonitorWorkorderRow> page = new Page<>(1, 20);
        page.addAll(Arrays.asList(noReport, nullQuantity));
        when(workorders.selectWorkorders(any(), any(), any(), anyList(), anyList())).thenReturn(page);

        ProductionMonitorQueryDTO query = new ProductionMonitorQueryDTO();
        query.setMonth("2026-09");
        List<ProductionMonitorWorkorderVO> result = service.workorders(query);

        assertEquals("NO_REPORTING_RECORD", result.get(0).getReportingCoverage());
        assertTrue(result.get(0).getRiskTags().contains(ProductionMonitorRiskTag.NO_REPORTING_RECORD));
        assertFalse(result.get(0).getRiskTags().contains(ProductionMonitorRiskTag.REPORTING_SILENCE));
        assertNull(result.get(0).getCompletionRatio());
        assertNull(result.get(1).getCompletionRatio());
    }

    @Test
    void reportingSilenceStartsAt72HoursForProductionWorkordersOnly() {
        ProductionMonitorWorkorderRow justUnder = workorder("WO-4", BigDecimal.TEN, BigDecimal.ONE);
        justUnder.setStatus("3");
        ProductionMonitorWorkorderRow exactlyAtBoundary = workorder("WO-5", BigDecimal.TEN, BigDecimal.ONE);
        exactlyAtBoundary.setStatus("3");
        ProductionMonitorWorkorderRow nonProduction = workorder("WO-6", BigDecimal.TEN, BigDecimal.ONE);
        nonProduction.setStatus("2");
        Page<ProductionMonitorWorkorderRow> page = new Page<>(1, 20);
        page.addAll(Arrays.asList(justUnder, exactlyAtBoundary, nonProduction));
        when(workorders.selectWorkorders(any(), any(), any(), anyList(), anyList())).thenReturn(page);
        LocalDateTime now = LocalDateTime.now();
        when(reporting.selectWorkorderAggregates(anyList(), anyString())).thenReturn(Arrays.asList(
                reportingAggregate("WO-4", now.minusHours(71).minusMinutes(59)),
                reportingAggregate("WO-5", now.minusHours(72)),
                reportingAggregate("WO-6", now.minusHours(90))));

        ProductionMonitorQueryDTO query = new ProductionMonitorQueryDTO();
        query.setMonth("2026-09");
        List<ProductionMonitorWorkorderVO> result = service.workorders(query);

        assertFalse(result.get(0).getRiskTags().contains(ProductionMonitorRiskTag.REPORTING_SILENCE));
        assertTrue(result.get(1).getRiskTags().contains(ProductionMonitorRiskTag.REPORTING_SILENCE));
        assertFalse(result.get(2).getRiskTags().contains(ProductionMonitorRiskTag.REPORTING_SILENCE));
    }

    @Test
    void detailIncludesLatestReportingProcessAndUserFields() {
        when(workorders.selectWorkorderById(1L)).thenReturn(workorder("WO-1", BigDecimal.TEN, BigDecimal.ONE));
        ProductionMonitorReportingAggregateRow latest = new ProductionMonitorReportingAggregateRow();
        latest.setWorkorderCode("WO-1");
        latest.setLastProcessId(2L);
        latest.setLastProcessCode("P-2");
        latest.setLastProcessName("车削");
        latest.setLastUserId(3L);
        latest.setLastUserName("operator");
        latest.setLastNickName("操作员");
        latest.setLastReportingTime(LocalDateTime.now().minusHours(1));
        when(reporting.selectLatestByWorkorderCodes(Collections.singletonList("WO-1")))
                .thenReturn(Collections.singletonList(latest));

        ProductionMonitorDetailVO result = service.detail(1L);

        assertEquals(2L, result.getLastProcessId());
        assertEquals("P-2", result.getLastProcessCode());
        assertEquals("车削", result.getLastProcessName());
        assertEquals(3L, result.getLastUserId());
        assertEquals("operator", result.getLastUserName());
        assertEquals("操作员", result.getLastNickName());
    }

    @Test
    void riskTagSerializesAsCodeLabelLevelObject() throws Exception {
        String json = new ObjectMapper().writeValueAsString(ProductionMonitorRiskTag.NO_REPORTING_RECORD);

        assertTrue(json.contains("\"code\":\"NO_REPORTING_RECORD\""));
        assertTrue(json.contains("\"label\":\"未发现本报工表关联记录\""));
        assertTrue(json.contains("\"level\":\"INFO\""));
    }

    @Test
    void routeDefinitionMissingKeepsObservedHistoricalEvents() {
        when(workorders.selectWorkorderById(5L)).thenReturn(workorder("WO-5", BigDecimal.TEN, BigDecimal.ONE));
        ProductionMonitorObservedProcessRow event = new ProductionMonitorObservedProcessRow();
        event.setProcessId(9L);
        event.setProcessCode("P9");
        event.setLastReportingTime(LocalDateTime.of(2026, 9, 29, 11, 0));
        event.setLastReportingRecordId(99L);
        when(reporting.selectObservedProcesses("WO-5", "FC-1", 3L)).thenReturn(Collections.singletonList(event));

        ProductionMonitorRouteTrackVO result = service.routeTrack(5L, "FC-1", 3L);

        assertTrue(result.isRouteDefinitionMissing());
        assertEquals(1, result.getObservedEvents().size());
        assertTrue(result.getObservedEvents().get(0).isObserved());
        assertTrue(result.getObservedEvents().get(0).isHistoricalOnly());
        assertEquals(LocalDateTime.of(2026, 9, 29, 11, 0), result.getObservedEvents().get(0).getLastReportingTime());
        assertEquals(99L, result.getObservedEvents().get(0).getLastReportingRecordId());
        assertEquals(1, result.getHistoricalOnlyProcesses().size());
    }

    @Test
    void factoryContextIsRequired() {
        FactoryContext.clear();
        assertThrows(RuntimeException.class, () -> service.summary("2026-09"));
    }

    private ProductionMonitorWorkorderRow workorder(String code, BigDecimal quantity, BigDecimal produced) {
        ProductionMonitorWorkorderRow row = new ProductionMonitorWorkorderRow();
        row.setWorkorderId(1L);
        row.setWorkorderCode(code);
        row.setProductCode("ITEM-1");
        row.setProductName("Item");
        row.setStatus("2");
        row.setQuantity(quantity);
        row.setQuantityProduced(produced);
        row.setRequestDate(LocalDateTime.of(2026, 9, 1, 0, 0));
        return row;
    }

    private ProductionMonitorReportingAggregateRow reportingAggregate(String code, LocalDateTime lastTime) {
        ProductionMonitorReportingAggregateRow row = new ProductionMonitorReportingAggregateRow();
        row.setWorkorderCode(code);
        row.setReportCount(1L);
        row.setFlowcardCount(1L);
        row.setLastReportingTime(lastTime);
        return row;
    }

    private List<SysDictData> workorderStatuses() {
        return Arrays.asList(dict("1", "未生产"), dict("2", "已发料"), dict("3", "生产中"), dict("Y", "已完工"));
    }

    private List<SysDictData> reportStatuses() {
        return Arrays.asList(dict("0", "未完结"), dict("1", "已完结"), dict("5", "全部入库"));
    }

    private SysDictData dict(String code, String label) {
        SysDictData value = new SysDictData();
        value.setDictValue(code);
        value.setDictLabel(label);
        return value;
    }
}
