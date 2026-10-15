package dev.mesbase.business.production.service;

import dev.mesbase.business.production.dao.MesProductionReportMapper;
import dev.mesbase.business.production.dao.ProductionQuantityMapper;
import dev.mesbase.business.basic.dao.WorkstationMapper;
import dev.mesbase.business.production.domain.MesProductionReportEntity;
import dev.mesbase.business.production.domain.MesProductionReportExample;
import dev.mesbase.business.production.domain.ReportRecordType;
import dev.mesbase.business.production.domain.ProductionReportModels.Supplement;
import dev.mesbase.business.production.domain.ProductionReportModels.Adjustment;
import dev.mesbase.business.production.domain.ProductionReportModels.VoidOriginal;
import dev.mesbase.business.production.domain.ProductionReportModels.ReportPage;
import dev.mesbase.business.production.domain.ProductionReportModels.Detail;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import com.github.pagehelper.PageHelper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProductionReportService {
    private final MesProductionReportMapper reports;
    private final ProductionQuantityMapper totals;
    private final ProductionQuantityService quantities;
    private final ProductionChainLocks locks;
    private final ReportUserReferenceService users;
    private final WorkstationMapper workstations;

    public ProductionReportService(MesProductionReportMapper reports, ProductionQuantityMapper totals,
                                   ProductionQuantityService quantities,
                                   ProductionChainLocks locks, ReportUserReferenceService users,
                                   WorkstationMapper workstations) {
        this.reports = reports;
        this.totals = totals;
        this.quantities = quantities;
        this.locks = locks;
        this.users = users;
        this.workstations = workstations;
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public MesProductionReportEntity supplement(Supplement command) {
        locks.execution(command.executionId());
        var view = quantities.getExecutionQuantities(command.executionId());
        positive(command.quantity());
        reason(command.reason());
        if (command.reportTime() == null || command.reportTime().isAfter(LocalDateTime.now())) invalid();
        users.requireExists(command.operatorUserId());
        if (command.workstationId() != null && workstations.selectByPrimaryKey(command.workstationId()) == null)
            throw new FoundationException(ErrorCode.REPORT_REFERENCE_INVALID);
        ProductionQuantityService.validateChange(view, command.routingNodeId(), command.quantity());
        var row = new MesProductionReportEntity();
        row.setExecutionId(command.executionId());
        row.setRoutingNodeId(command.routingNodeId());
        row.setRecordType(ReportRecordType.REPORT.getValue());
        row.setQuantityDelta(command.quantity());
        row.setOperatorUserId(command.operatorUserId());
        row.setReportTime(command.reportTime());
        row.setWorkstationId(command.workstationId());
        row.setReason(command.reason().trim());
        return append(row);
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public MesProductionReportEntity adjust(Long id, Adjustment command) {
        var original = lockedOriginal(id);
        delta(command.quantityDelta());
        reason(command.reason());
        return adjustLocked(original, command.quantityDelta(), command.reason());
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public MesProductionReportEntity voidOriginalReport(Long id, VoidOriginal command) {
        var original = lockedOriginal(id);
        reason(command.reason());
        var balance = totals.originalBalance(original.getExecutionId(), id).effectiveQty();
        if (balance.signum() <= 0) throw new FoundationException(ErrorCode.REPORT_ORIGINAL_EMPTY);
        return adjustLocked(original, balance.negate(), command.reason());
    }

    private MesProductionReportEntity adjustLocked(MesProductionReportEntity original, BigDecimal delta,
                                                   String reason) {
        var balance = totals.originalBalance(original.getExecutionId(), original.getId()).effectiveQty();
        if (balance.add(delta).signum() < 0) throw new FoundationException(ErrorCode.REPORT_QUANTITY_CONFLICT);
        var view = quantities.getExecutionQuantities(original.getExecutionId());
        ProductionQuantityService.validateChange(view, original.getRoutingNodeId(), delta);
        var row = new MesProductionReportEntity();
        row.setExecutionId(original.getExecutionId());
        row.setRoutingNodeId(original.getRoutingNodeId());
        row.setRecordType(ReportRecordType.ADJUSTMENT.getValue());
        row.setQuantityDelta(delta);
        row.setReferenceReportId(original.getId());
        row.setOperatorUserId(original.getOperatorUserId());
        row.setReportTime(original.getReportTime());
        row.setWorkstationId(original.getWorkstationId());
        row.setReason(reason.trim());
        return append(row);
    }

    private MesProductionReportEntity lockedOriginal(Long id) {
        var before = require(id);
        locks.execution(before.getExecutionId());
        var original = require(id);
        if (!ReportRecordType.REPORT.getValue().equals(original.getRecordType()))
            throw new FoundationException(ErrorCode.REPORT_REFERENCE_INVALID);
        return original;
    }

    private MesProductionReportEntity append(MesProductionReportEntity row) {
        row.setRecordedBy(SecurityUtils.getUserId());
        row.setCreateTime(LocalDateTime.now());
        if (reports.insert(row) != 1) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        return reports.selectByPrimaryKey(row.getId());
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public Detail getDetail(Long id) {
        var row = require(id);
        Long original = ReportRecordType.REPORT.getValue()
                .equals(row.getRecordType()) ? row.getId() : row.getReferenceReportId();
        var balance = totals.originalBalance(row.getExecutionId(), original);
        if (balance == null) throw new FoundationException(ErrorCode.REPORT_REFERENCE_INVALID);
        return new Detail(row, balance.effectiveQty(), names(List.of(row)));
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public ReportPage page(Long executionId, Long nodeId, int offset, int limit) {
        quantities.getNodeQuantity(executionId, nodeId);
        var f = new MesProductionReportExample();
        f.createCriteria().andExecutionIdEqualTo(executionId).andRoutingNodeIdEqualTo(nodeId);
        return page(f, offset, limit);
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public ReportPage adjustments(Long originalId, int offset, int limit) {
        var original = require(originalId);
        if (!ReportRecordType.REPORT.getValue().equals(original.getRecordType()))
            throw new FoundationException(ErrorCode.REPORT_REFERENCE_INVALID);
        var f = new MesProductionReportExample();
        f.createCriteria().andReferenceReportIdEqualTo(originalId).andExecutionIdEqualTo(original.getExecutionId());
        return page(f, offset, limit);
    }

    private ReportPage page(MesProductionReportExample f, int offset, int limit) {
        ProductionQuantityService.paging(offset, limit);
        f.setOrderByClause("create_time desc,id desc");
        List<MesProductionReportEntity> rows;
        long total;
        try (var page = PageHelper.offsetPage(offset, limit)) {
            rows = List.copyOf(reports.selectByExample(f));
            total = page.getTotal();
        }
        return new ReportPage(rows, total, offset, limit, names(rows));
    }

    private Map<Long, String> names(List<MesProductionReportEntity> rows) {
        return users.names(rows.stream().flatMap(row -> Stream.of(row.getOperatorUserId(), row.getRecordedBy()))
                .toList());
    }

    private MesProductionReportEntity require(Long id) {
        var row = reports.selectByPrimaryKey(id);
        if (row == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        return row;
    }

    private static void reason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500) invalid();
    }

    private static void positive(BigDecimal value) {
        delta(value);
        if (value.signum() < 0) invalid();
    }

    private static void delta(BigDecimal value) {
        if (value == null || value.signum() == 0 || value.scale() > 6 || value.precision() - value.scale() > 14)
            invalid();
    }

    private static void invalid() {
        throw new FoundationException(ErrorCode.BAD_REQUEST);
    }
}
