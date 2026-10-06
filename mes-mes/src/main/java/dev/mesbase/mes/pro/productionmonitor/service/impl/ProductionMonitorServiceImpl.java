package dev.mesbase.mes.pro.productionmonitor.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.common.core.domain.entity.SysDictData;
import dev.mesbase.common.exception.ServiceException;
import dev.mesbase.common.core.redis.RedisCache;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.common.utils.DictUtils;
import dev.mesbase.mes.enumeration.DicEnums;
import dev.mesbase.mes.pro.productionmonitor.domain.dto.ProductionMonitorQueryDTO;
import dev.mesbase.mes.pro.productionmonitor.domain.dto.ProductionMonitorTimelineQueryDTO;
import dev.mesbase.mes.pro.productionmonitor.domain.query.ProductionMonitorTimelineQuery;
import dev.mesbase.mes.pro.productionmonitor.domain.row.*;
import dev.mesbase.mes.pro.productionmonitor.domain.vo.*;
import dev.mesbase.mes.pro.productionmonitor.mapper.ProReportingWorkMonitorExtraMapper;
import dev.mesbase.mes.pro.productionmonitor.mapper.ProWorkorderMonitorExtraMapper;
import dev.mesbase.mes.pro.productionmonitor.mapper.QcDefectiveMonitorExtraMapper;
import dev.mesbase.mes.pro.productionmonitor.service.ProductionMonitorService;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="mes.analytics.enabled",havingValue="true")
@Service
public class ProductionMonitorServiceImpl implements ProductionMonitorService {
    private static final String WORKORDER_STATUS_DICT = "mes_order_status";
    private static final String REPORT_STATUS_DICT = "mes_feedback_status";
    private static final int NEAR_PLAN_DAYS = 3;
    private static final BigDecimal LOW_COMPLETION_RATIO = new BigDecimal("0.25");
    private static final int REPORT_SILENCE_HOURS = 72;
    private static final String COVERAGE_HAS = "HAS_RECORD";
    private static final String COVERAGE_MISSING = "NO_REPORTING_RECORD";

    private final ProWorkorderMonitorExtraMapper workorders;
    private final ProReportingWorkMonitorExtraMapper reporting;
    private final QcDefectiveMonitorExtraMapper defective;
    private final RedisCache redisCache;

    public ProductionMonitorServiceImpl(ProWorkorderMonitorExtraMapper workorders,
                                        ProReportingWorkMonitorExtraMapper reporting,
                                        QcDefectiveMonitorExtraMapper defective,
                                        RedisCache redisCache) {
        this.workorders = workorders;
        this.reporting = reporting;
        this.defective = defective;
        this.redisCache = redisCache;
    }

    @Override
    public ProductionMonitorSummaryVO summary(String monthText) {
        FactoryContext.required();
        YearMonth month = parseMonth(monthText);
        MonitorClock clock = clock();
        StatusMetadata statuses = statusMetadata();
        ProductionMonitorQueryDTO query = queryForMonth(month);
        ProductionMonitorSummaryRow row = workorders.selectSummary(query, clock.today, clock.now,
                statuses.terminalCodes, statuses.productionCodes);
        ProductionMonitorSummaryVO result = new ProductionMonitorSummaryVO();
        result.setMonth(month.toString());
        if (row != null) {
            result.setTotalWorkorders(value(row.getTotalWorkorders()));
            result.setReportingCoveredCount(value(row.getReportingCoveredCount()));
            result.setReportingMissingCount(value(row.getReportingMissingCount()));
            result.setPlanDatePassedCount(value(row.getPlanDatePassedCount()));
            result.setNearPlanDateLowCompletionCount(value(row.getNearPlanDateLowCompletionCount()));
            result.setReportSilenceCount(value(row.getReportSilenceCount()));
            result.setDefectiveWorkorderCount(value(row.getDefectiveWorkorderCount()));
        }
        for (ProductionMonitorStatusCountRow statusRow : workorders.selectSummaryStatusCounts(query)) {
            ProductionMonitorStatusCountVO status = new ProductionMonitorStatusCountVO();
            status.setStatusCode(statusRow.getStatusCode());
            status.setStatusName(statusLabel(statuses, statusRow.getStatusCode()));
            status.setCount(value(statusRow.getCount()));
            result.getStatusCounts().add(status);
            if (statuses.productionCodes.contains(statusRow.getStatusCode())) {
                result.setInProductionCount(result.getInProductionCount() + value(statusRow.getCount()));
            }
        }
        result.setAttentionWorkorderCount(workorders.countAttentionWorkorders(query, clock.today, clock.now,
                statuses.terminalCodes, statuses.productionCodes));
        for (ProductionMonitorAggregateCountRow aggregate : workorders.selectSummaryCompletionBuckets(query)) {
            result.getCompletionBuckets().add(toAggregateCount(aggregate));
        }
        for (ProductionMonitorAggregateCountRow aggregate : workorders.selectSummaryAlertCounts(query, clock.today, clock.now,
                statuses.terminalCodes, statuses.productionCodes)) {
            ProductionMonitorAggregateCountVO alert = toAggregateCount(aggregate);
            alert.setLevel(alertLevel(aggregate.getCode()));
            result.getAlertCounts().add(alert);
        }
        return result;
    }

    @Override
    public List<ProductionMonitorWorkorderVO> workorders(ProductionMonitorQueryDTO query) {
        FactoryContext.required();
        normalizeQuery(query);
        MonitorClock clock = clock();
        StatusMetadata statuses = statusMetadata();
        List<ProductionMonitorWorkorderRow> rows = workorders.selectWorkorders(query, clock.today, clock.now,
                statuses.terminalCodes, statuses.productionCodes);
        if (rows.isEmpty()) return emptyPage(query, rows);
        List<String> codes = rows.stream().map(ProductionMonitorWorkorderRow::getWorkorderCode).distinct().collect(Collectors.toList());
        Map<String, ProductionMonitorReportingAggregateRow> reportByCode = index(reporting.selectWorkorderAggregates(codes,
                DicEnums.MES_FEEDBACK_STATUS_ALL_WAREHOUSING.getValue()),
                ProductionMonitorReportingAggregateRow::getWorkorderCode);
        Map<String, ProductionMonitorReportingAggregateRow> latestByCode = index(reporting.selectLatestByWorkorderCodes(codes),
                ProductionMonitorReportingAggregateRow::getWorkorderCode);
        Map<String, ProductionMonitorDefectiveAggregateRow> defectByCode = index(defective.selectWorkorderAggregates(codes),
                ProductionMonitorDefectiveAggregateRow::getWorkorderCode);
        List<ProductionMonitorWorkorderVO> values = new ArrayList<>(rows.size());
        for (ProductionMonitorWorkorderRow row : rows) {
            ProductionMonitorWorkorderVO value = toWorkorder(row, reportByCode.get(row.getWorkorderCode()),
                    latestByCode.get(row.getWorkorderCode()), defectByCode.get(row.getWorkorderCode()), clock, statuses);
            values.add(value);
        }
        return preservePageWithQuery(rows, values, query);
    }

    @Override
    public ProductionMonitorDetailVO detail(Long workorderId) {
        FactoryContext.required();
        if (workorderId == null) throw new ServiceException("工单ID不能为空");
        ProductionMonitorWorkorderRow row = workorders.selectWorkorderById(workorderId);
        if (row == null) throw new ServiceException("工单不存在");
        MonitorClock clock = clock();
        StatusMetadata statuses = statusMetadata();
        String code = row.getWorkorderCode();
        ProductionMonitorReportingAggregateRow aggregate = first(reporting.selectWorkorderAggregates(Collections.singletonList(code),
                DicEnums.MES_FEEDBACK_STATUS_ALL_WAREHOUSING.getValue()));
        ProductionMonitorReportingAggregateRow latest = first(reporting.selectLatestByWorkorderCodes(Collections.singletonList(code)));
        ProductionMonitorDefectiveAggregateRow defectRow = first(defective.selectWorkorderAggregates(Collections.singletonList(code)));
        ProductionMonitorWorkorderVO summary = toWorkorder(row, aggregate, latest, defectRow, clock, statuses);
        ProductionMonitorDetailVO result = toDetail(row, summary);
        for (ProductionMonitorFlowcardRow flowcard : reporting.selectFlowcardsByWorkorderCode(code,
                DicEnums.MES_FEEDBACK_STATUS_ALL_WAREHOUSING.getValue())) {
            ProductionMonitorFlowcardVO vo = new ProductionMonitorFlowcardVO();
            vo.setXtransferNo(flowcard.getXtransferNo());
            vo.setRouteId(flowcard.getRouteId());
            vo.setRouteName(flowcard.getRouteName());
            vo.setReportCount(value(flowcard.getReportCount()));
            vo.setFirstReportingTime(flowcard.getFirstReportingTime());
            vo.setLastReportingTime(flowcard.getLastReportingTime());
            vo.setFullyStored(flowcard.isFullyStored());
            result.getFlowcards().add(vo);
        }
        return result;
    }

    @Override
    public List<ProductionMonitorTimelineEventVO> timeline(Long workorderId, ProductionMonitorTimelineQueryDTO query) {
        FactoryContext.required();
        ProductionMonitorWorkorderRow row = requireWorkorder(workorderId);
        ProductionMonitorTimelineQuery timelineQuery = new ProductionMonitorTimelineQuery();
        timelineQuery.setWorkorderCode(row.getWorkorderCode());
        timelineQuery.setXtransferNo(query.getXtransferNo());
        timelineQuery.setRouteId(query.getRouteId());
        timelineQuery.setProcessId(query.getProcessId());
        timelineQuery.setProcessCode(query.getProcessCode());
        PageHelper.startPage(query.getPageNum(), query.getPageSize());
        List<ProductionMonitorTimelineRow> rows = reporting.selectTimeline(timelineQuery);
        List<ProductionMonitorTimelineEventVO> values = new ArrayList<>(rows.size());
        for (ProductionMonitorTimelineRow event : rows) {
            ProductionMonitorTimelineEventVO vo = new ProductionMonitorTimelineEventVO();
            vo.setRecordId(event.getRecordId());
            vo.setCreateTime(event.getCreateTime());
            vo.setXtransferNo(event.getXtransferNo());
            vo.setRouteId(event.getRouteId());
            vo.setRouteName(event.getRouteName());
            vo.setProcessId(event.getProcessId());
            vo.setProcessCode(event.getProcessCode());
            vo.setProcessName(event.getProcessName());
            vo.setUnitOfMeasure(event.getUnitOfMeasure());
            vo.setUserId(event.getUserId());
            vo.setUserName(event.getUserName());
            vo.setNickName(event.getNickName());
            vo.setReportingWorkQuantity(event.getReportingWorkQuantity());
            vo.setStatus(event.getStatus());
            vo.setStatusName(dictLabel(REPORT_STATUS_DICT, event.getStatus()));
            values.add(vo);
        }
        return preservePageFromSource(rows, values);
    }

    @Override
    public ProductionMonitorRouteTrackVO routeTrack(Long workorderId, String xtransferNo, Long routeId) {
        FactoryContext.required();
        ProductionMonitorWorkorderRow workorder = requireWorkorder(workorderId);
        if (xtransferNo == null || xtransferNo.trim().isEmpty() || routeId == null)
            throw new ServiceException("流程卡和路线不能为空");
        List<ProductionMonitorRouteStepRow> definitionRows = workorders.selectRouteDefinition(routeId);
        List<ProductionMonitorObservedProcessRow> observedRows = reporting.selectObservedProcesses(
                workorder.getWorkorderCode(), xtransferNo, routeId);
        ProductionMonitorRouteTrackVO result = new ProductionMonitorRouteTrackVO();
        result.setRouteDefinitionMissing(definitionRows.isEmpty());
        Set<String> definitionKeys = new HashSet<>();
        for (ProductionMonitorRouteStepRow row : definitionRows) {
            ProductionMonitorRouteTrackVO.ProductionMonitorRouteStepVO vo = new ProductionMonitorRouteTrackVO.ProductionMonitorRouteStepVO();
            vo.setOrderNum(row.getOrderNum());
            vo.setProcessId(row.getProcessId());
            vo.setProcessCode(row.getProcessCode());
            vo.setProcessName(row.getProcessName());
            result.getRouteDefinition().add(vo);
            definitionKeys.add(processKey(row.getProcessId(), row.getProcessCode()));
        }
        for (ProductionMonitorObservedProcessRow row : observedRows) {
            ProductionMonitorRouteTrackVO.ProductionMonitorObservedProcessVO vo = new ProductionMonitorRouteTrackVO.ProductionMonitorObservedProcessVO();
            vo.setProcessId(row.getProcessId());
            vo.setProcessCode(row.getProcessCode());
            vo.setProcessName(row.getProcessName());
            vo.setFirstReportingTime(row.getFirstReportingTime());
            vo.setLastReportingTime(row.getLastReportingTime());
            vo.setLastReportingRecordId(row.getLastReportingRecordId());
            vo.setReportCount(value(row.getReportCount()));
            vo.setReportedQuantity(row.getReportedQuantity());
            vo.setObserved(true);
            vo.setHistoricalOnly(definitionRows.isEmpty() || !definitionKeys.contains(processKey(row.getProcessId(), row.getProcessCode())));
            result.getObservedEvents().add(vo);
            if (vo.isHistoricalOnly()) result.getHistoricalOnlyProcesses().add(vo);
        }
        Set<String> observedKeys = observedRows.stream()
                .map(row -> processKey(row.getProcessId(), row.getProcessCode())).collect(Collectors.toSet());
        for (ProductionMonitorRouteTrackVO.ProductionMonitorRouteStepVO step : result.getRouteDefinition())
            step.setObserved(observedKeys.contains(processKey(step.getProcessId(), step.getProcessCode())));
        return result;
    }

    private ProductionMonitorWorkorderVO toWorkorder(ProductionMonitorWorkorderRow row,
                                                       ProductionMonitorReportingAggregateRow aggregate,
                                                       ProductionMonitorReportingAggregateRow latest,
                                                       ProductionMonitorDefectiveAggregateRow defectRow,
                                                       MonitorClock clock, StatusMetadata statuses) {
        ProductionMonitorWorkorderVO value = new ProductionMonitorWorkorderVO();
        value.setWorkorderId(row.getWorkorderId());
        value.setWorkorderCode(row.getWorkorderCode());
        value.setWorkorderName(row.getWorkorderName());
        value.setProductCode(row.getProductCode());
        value.setProductName(row.getProductName());
        value.setStatus(row.getStatus());
        value.setStatusName(statusLabel(statuses, row.getStatus()));
        value.setQuantity(row.getQuantity());
        value.setQuantityProduced(row.getQuantityProduced());
        value.setCompletionRatio(completionRatio(row.getQuantityProduced(), row.getQuantity()));
        value.setRequestDate(row.getRequestDate());
        value.setFinishDate(row.getFinishDate());
        value.setTransactionDate(row.getTransactionDate());
        value.setReportingCoverage(aggregate == null ? COVERAGE_MISSING : COVERAGE_HAS);
        value.setReportCount(aggregate == null ? 0 : value(aggregate.getReportCount()));
        value.setFlowcardCount(aggregate == null ? 0 : value(aggregate.getFlowcardCount()));
        value.setFullyStoredFlowcardCount(aggregate == null ? 0 : value(aggregate.getFullyStoredFlowcardCount()));
        value.setLastReportingTime(aggregate == null ? null : aggregate.getLastReportingTime());
        if (latest != null) {
            value.setLastReportingTime(latest.getLastReportingTime());
            value.setLastProcessId(latest.getLastProcessId());
            value.setLastProcessCode(latest.getLastProcessCode());
            value.setLastProcessName(latest.getLastProcessName());
            value.setLastUserId(latest.getLastUserId());
            value.setLastUserName(latest.getLastUserName());
            value.setLastNickName(latest.getLastNickName());
        }
        value.setDefectiveRecordCount(defectRow == null ? 0 : value(defectRow.getDefectiveRecordCount()));
        addRiskTags(value, row, clock, statuses);
        return value;
    }

    private ProductionMonitorDetailVO toDetail(ProductionMonitorWorkorderRow row, ProductionMonitorWorkorderVO summary) {
        ProductionMonitorDetailVO value = new ProductionMonitorDetailVO();
        value.setWorkorderId(row.getWorkorderId());
        value.setWorkorderCode(row.getWorkorderCode());
        value.setWorkorderName(row.getWorkorderName());
        value.setOrderSource(row.getOrderSource());
        value.setSourceCode(row.getSourceCode());
        value.setProductId(row.getProductId());
        value.setProductCode(row.getProductCode());
        value.setProductName(row.getProductName());
        value.setProductSpc(row.getProductSpc());
        value.setUnitOfMeasure(row.getUnitOfMeasure());
        value.setQuantity(row.getQuantity());
        value.setQuantityProduced(row.getQuantityProduced());
        value.setCompletionRatio(summary.getCompletionRatio());
        value.setClientId(row.getClientId());
        value.setClientCode(row.getClientCode());
        value.setClientName(row.getClientName());
        value.setRequestDate(row.getRequestDate());
        value.setFinishDate(row.getFinishDate());
        value.setTransactionDate(row.getTransactionDate());
        value.setStatus(row.getStatus());
        value.setStatusName(summary.getStatusName());
        value.setReportingCoverage(summary.getReportingCoverage());
        value.setReportCount(summary.getReportCount());
        value.setFlowcardCount(summary.getFlowcardCount());
        value.setLastReportingTime(summary.getLastReportingTime());
        value.setLastProcessId(summary.getLastProcessId());
        value.setLastProcessCode(summary.getLastProcessCode());
        value.setLastProcessName(summary.getLastProcessName());
        value.setLastUserId(summary.getLastUserId());
        value.setLastUserName(summary.getLastUserName());
        value.setLastNickName(summary.getLastNickName());
        value.setDefectiveRecordCount(summary.getDefectiveRecordCount());
        value.setFullyStoredFlowcardCount(summary.getFullyStoredFlowcardCount());
        value.setRiskTags(summary.getRiskTags());
        return value;
    }

    private void addRiskTags(ProductionMonitorWorkorderVO target, ProductionMonitorWorkorderRow row,
                             MonitorClock clock, StatusMetadata statuses) {
        LocalDate finishDate = row.getFinishDate() == null ? null : row.getFinishDate().toLocalDate();
        if (finishDate != null && finishDate.isBefore(clock.today) && !statuses.terminalCodes.contains(row.getStatus()))
            target.getRiskTags().add(ProductionMonitorRiskTag.PLAN_DATE_PASSED);
        if (finishDate != null && !finishDate.isBefore(clock.today) && !finishDate.isAfter(clock.today.plusDays(NEAR_PLAN_DAYS)))
            target.getRiskTags().add(ProductionMonitorRiskTag.NEAR_PLAN_DATE);
        BigDecimal ratio = completionRatio(row.getQuantityProduced(), row.getQuantity());
        if (ratio != null && ratio.compareTo(LOW_COMPLETION_RATIO) < 0)
            target.getRiskTags().add(ProductionMonitorRiskTag.LOW_STORED_COMPLETION);
        if (COVERAGE_MISSING.equals(target.getReportingCoverage()))
            target.getRiskTags().add(ProductionMonitorRiskTag.NO_REPORTING_RECORD);
        else if (statuses.productionCodes.contains(row.getStatus()) && target.getLastReportingTime() != null
                && !target.getLastReportingTime().isAfter(clock.now.minusHours(REPORT_SILENCE_HOURS)))
            target.getRiskTags().add(ProductionMonitorRiskTag.REPORTING_SILENCE);
        if (target.getDefectiveRecordCount() > 0)
            target.getRiskTags().add(ProductionMonitorRiskTag.HAS_DEFECTIVE_RECORD);
    }

    private void normalizeQuery(ProductionMonitorQueryDTO query) {
        if (query == null) throw new ServiceException("查询条件不能为空");
        YearMonth month = parseMonth(query.getMonth());
        query.setMonth(month.toString());
        query.setMonthStart(month.atDay(1).atStartOfDay());
        query.setNextMonthStart(month.plusMonths(1).atDay(1).atStartOfDay());
        if (query.getPageNum() == null || query.getPageNum() < 1) query.setPageNum(1);
        if (query.getPageSize() == null || query.getPageSize() < 1) query.setPageSize(20);
        if (query.getPageSize() > 100) throw new ServiceException("pageSize 不能超过 100");
        if (query.getFinishDateFrom() != null && query.getFinishDateTo() != null
                && query.getFinishDateFrom().isAfter(query.getFinishDateTo())) throw new ServiceException("finishDateFrom 不能晚于 finishDateTo");
        if (query.getCoverage() != null && !query.getCoverage().isEmpty()
                && !COVERAGE_HAS.equals(query.getCoverage()) && !COVERAGE_MISSING.equals(query.getCoverage()))
            throw new ServiceException("不支持的 reportingCoverage");
        if (query.getRiskTag() != null && !query.getRiskTag().isEmpty()) {
            try { ProductionMonitorRiskTag.valueOf(query.getRiskTag()); }
            catch (IllegalArgumentException invalid) { throw new ServiceException("不支持的 riskTag"); }
        }
    }

    private ProductionMonitorAggregateCountVO toAggregateCount(ProductionMonitorAggregateCountRow row) {
        ProductionMonitorAggregateCountVO value = new ProductionMonitorAggregateCountVO();
        value.setCode(row.getCode());
        value.setLabel("COMPLETION".equals(row.getCategory()) ? completionBucketLabel(row.getCode())
                : ProductionMonitorRiskTag.valueOf(row.getCode()).getLabel());
        value.setCategory(row.getCategory());
        value.setCount(value(row.getCount()));
        return value;
    }

    private String completionBucketLabel(String code) {
        return code.replace("-", "–");
    }

    private String alertLevel(String code) {
        if (ProductionMonitorRiskTag.PLAN_DATE_PASSED.name().equals(code)
                || ProductionMonitorRiskTag.HAS_DEFECTIVE_RECORD.name().equals(code)) return "danger";
        if (ProductionMonitorRiskTag.NO_REPORTING_RECORD.name().equals(code)) return "info";
        return "warning";
    }

    private ProductionMonitorQueryDTO queryForMonth(YearMonth month) {
        ProductionMonitorQueryDTO query = new ProductionMonitorQueryDTO();
        query.setMonth(month.toString());
        query.setMonthStart(month.atDay(1).atStartOfDay());
        query.setNextMonthStart(month.plusMonths(1).atDay(1).atStartOfDay());
        return query;
    }

    private YearMonth parseMonth(String monthText) {
        if (monthText == null || monthText.trim().isEmpty()) return YearMonth.now(ZoneId.systemDefault());
        try { return YearMonth.parse(monthText); }
        catch (RuntimeException invalid) { throw new ServiceException("month 格式必须为 YYYY-MM"); }
    }

    private MonitorClock clock() {
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        return new MonitorClock(now.toLocalDate(), now);
    }

    private StatusMetadata statusMetadata() {
        List<SysDictData> dictionary = redisCache.getCacheObject(DictUtils.getCacheKey(WORKORDER_STATUS_DICT));
        if (dictionary == null || dictionary.isEmpty()) throw new ServiceException("工单状态字典不可用");
        StatusMetadata result = new StatusMetadata();
        for (SysDictData item : dictionary) {
            String code = item.getDictValue();
            String label = item.getDictLabel();
            if (code == null || label == null) continue;
            result.labels.put(code, label);
            if (label.contains("生产中")) result.productionCodes.add(code);
            if (label.contains("完工") || label.contains("完成")) result.terminalCodes.add(code);
        }
        if (result.terminalCodes.isEmpty()) throw new ServiceException("工单状态字典未配置完成终态");
        if (result.productionCodes.isEmpty()) throw new ServiceException("工单状态字典未配置生产中状态");
        return result;
    }

    private String statusLabel(StatusMetadata statuses, String code) {
        return code == null ? null : statuses.labels.getOrDefault(code, code);
    }

    private String dictLabel(String type, String code) {
        if (code == null) return null;
        List<SysDictData> dictionary = redisCache.getCacheObject(DictUtils.getCacheKey(type));
        if (dictionary == null) return code;
        for (SysDictData item : dictionary) if (code.equals(item.getDictValue())) return item.getDictLabel();
        return code;
    }

    private BigDecimal completionRatio(BigDecimal produced, BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0 || produced == null) return null;
        return produced.divide(quantity, 6, RoundingMode.HALF_UP);
    }

    private ProductionMonitorWorkorderRow requireWorkorder(Long workorderId) {
        if (workorderId == null) throw new ServiceException("工单ID不能为空");
        ProductionMonitorWorkorderRow row = workorders.selectWorkorderById(workorderId);
        if (row == null) throw new ServiceException("工单不存在");
        return row;
    }

    private String processKey(Long processId, String processCode) { return String.valueOf(processId) + "|" + String.valueOf(processCode); }

    private long value(Long value) { return value == null ? 0L : value; }

    private <T> T first(List<T> rows) { return rows == null || rows.isEmpty() ? null : rows.get(0); }

    private <T, K> Map<K, T> index(List<T> rows, Function<T, K> key) {
        return rows.stream().collect(Collectors.toMap(key, Function.identity(), (left, right) -> left));
    }

    private List<ProductionMonitorWorkorderVO> preservePageWithQuery(List<?> source, List<ProductionMonitorWorkorderVO> rows,
                                                                      ProductionMonitorQueryDTO query) {
        long total = source instanceof Page ? ((Page<?>) source).getTotal() : rows.size();
        Page<ProductionMonitorWorkorderVO> page = new Page<>(query.getPageNum(), query.getPageSize());
        page.setTotal(total);
        page.addAll(rows);
        return page;
    }

    private <T> List<T> preservePageFromSource(List<?> source, List<T> rows) {
        Page<?> sourcePage = source instanceof Page ? (Page<?>) source : null;
        long total = sourcePage == null ? rows.size() : sourcePage.getTotal();
        int pageNum = sourcePage == null ? 1 : sourcePage.getPageNum();
        int pageSize = sourcePage == null ? Math.max(rows.size(), 1) : sourcePage.getPageSize();
        Page<T> page = new Page<>(pageNum, pageSize);
        page.setTotal(total);
        page.addAll(rows);
        return page;
    }

    private List<ProductionMonitorWorkorderVO> emptyPage(ProductionMonitorQueryDTO query, List<?> source) {
        return preservePageWithQuery(source, Collections.emptyList(), query);
    }

    @Data @AllArgsConstructor
    private static class MonitorClock {
        private LocalDate today;
        private LocalDateTime now;
    }

    @Data
    private static class StatusMetadata {
        private Map<String, String> labels = new HashMap<>();
        private List<String> terminalCodes = new ArrayList<>();
        private List<String> productionCodes = new ArrayList<>();
    }
}
