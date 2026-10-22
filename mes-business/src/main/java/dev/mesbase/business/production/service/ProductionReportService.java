package dev.mesbase.business.production.service;

import dev.mesbase.common.validation.ParameterChecks;
import java.util.Optional;

import dev.mesbase.business.production.domain.ProductionQuantityInput;
import dev.mesbase.business.production.domain.ProductionCompletionRequested;
import dev.mesbase.platform.execution.FactoryContext;
import org.springframework.context.ApplicationEventPublisher;

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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.Map;
import java.util.stream.Collectors;
import dev.mesbase.business.directory.service.BusinessDirectoryService;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProductionReportService {
    private final BusinessSourceRules sources;
    private final ApplicationEventPublisher publisher;

    private enum AdminAction {SUPPLEMENT, ADJUST, VOID}

    private final MesProductionReportMapper reports;
    private final ProductionQuantityMapper totals;
    private final ProductionQuantityService quantities;
    private final ProductionChainLocks locks;
    private final ReportUserReferenceService users;
    private final BusinessDirectoryService references;
    private final WorkstationMapper workstations;

    public ProductionReportService(MesProductionReportMapper reports, ProductionQuantityMapper totals,
                                   ProductionQuantityService quantities,
                                   ProductionChainLocks locks, ReportUserReferenceService users,
                                   WorkstationMapper workstations, BusinessDirectoryService references,
                                   BusinessSourceRules sources, ApplicationEventPublisher publisher) {
        this.publisher = publisher;
        this.sources = sources;
        this.reports = reports;
        this.totals = totals;
        this.quantities = quantities;
        this.locks = locks;
        this.users = users;
        this.references = references;
        this.workstations = workstations;
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public MesProductionReportEntity supplement(Supplement command) {
        String hash = fingerprint(AdminAction.SUPPLEMENT, command.executionId(), command.routingNodeId(), command.quantity(),
                command.operatorUserId(), command.reportTime(), command.workstationId(), command.reason());
        var existing = replay(command.requestId(), hash);
        if (existing != null) return existing;
        locks.execution(command.executionId());
        existing = replay(command.requestId(), hash);
        if (existing != null) return existing;
        sources.execution(locks.execution(command.executionId()), null);
        ProductionQuantityInput.check(command.quantity());
        positive(command.quantity());
        reason(command.reason());
        if (command.reportTime() == null || command.reportTime().isAfter(LocalDateTime.now())) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        users.requireExists(command.operatorUserId());
        if (command.workstationId() != null && workstations.selectByPrimaryKey(command.workstationId()) == null)
            throw new FoundationException(ErrorCode.REPORT_REFERENCE_INVALID);
        quantities.validateReportChange(command.executionId(), command.routingNodeId(), command.quantity());
        var row = new MesProductionReportEntity();
        row.setExecutionId(command.executionId());
        row.setRoutingNodeId(command.routingNodeId());
        row.setRecordType(ReportRecordType.REPORT.getValue());
        row.setQuantityDelta(command.quantity());
        row.setOperatorUserId(command.operatorUserId());
        row.setReportTime(command.reportTime());
        row.setWorkstationId(command.workstationId());
        row.setReason(command.reason().trim());
        return append(row, command.requestId(), hash);
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public MesProductionReportEntity adjust(Long id, Adjustment command) {
        String hash = fingerprint(AdminAction.ADJUST, id, command.quantityDelta(), command.reason());
        var existing = replay(command.requestId(), hash);
        if (existing != null) return existing;
        var original = lockedOriginal(id);
        existing = replay(command.requestId(), hash);
        if (existing != null) return existing;
        if (command.quantityDelta() != null && command.quantityDelta().signum() > 0)
            locks.requireActiveExecution(original.getExecutionId());
        ProductionQuantityInput.check(command.quantityDelta());
        delta(command.quantityDelta());
        reason(command.reason());
        return adjustLocked(original, command.quantityDelta(), command.reason(), command.requestId(), hash);
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public MesProductionReportEntity voidOriginalReport(Long id, VoidOriginal command) {
        String hash = fingerprint(AdminAction.VOID, id, command.reason());
        var existing = replay(command.requestId(), hash);
        if (existing != null) return existing;
        var original = lockedOriginal(id);
        existing = replay(command.requestId(), hash);
        if (existing != null) return existing;
        reason(command.reason());
        var balance = totals.originalBalance(original.getExecutionId(), id).effectiveQty();
        if (balance.signum() <= 0) throw new FoundationException(ErrorCode.REPORT_ORIGINAL_EMPTY);
        return adjustLocked(original, balance.negate(), command.reason(), command.requestId(), hash);
    }

    private MesProductionReportEntity adjustLocked(MesProductionReportEntity original, BigDecimal delta,
                                                   String reason, String requestId, String hash) {
        var balance = totals.originalBalance(original.getExecutionId(), original.getId()).effectiveQty();
        if (balance.add(delta).signum() < 0) throw new FoundationException(ErrorCode.REPORT_QUANTITY_CONFLICT);
        quantities.validateReportChange(original.getExecutionId(), original.getRoutingNodeId(), delta);
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
        return append(row, requestId, hash);
    }

    /**
     * Returns the reported quantity to this node's normal reporting allowance without deleting facts.
     */
    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void returnToNode(Long executionId, Long nodeId, BigDecimal quantity, String qualityEventId, String reason) {
        locks.requireActiveExecution(executionId);
        sources.execution(locks.execution(executionId), null);
        positive(quantity);
        ProductionQuantityInput.check(quantity);
        var filter = new MesProductionReportExample();
        filter.createCriteria().andExecutionIdEqualTo(executionId).andRoutingNodeIdEqualTo(nodeId);
        filter.setOrderByClause("id desc");
        List<MesProductionReportEntity> rows;
        try (var page = PageHelper.offsetPage(0, 10001, false)) {
            rows = List.copyOf(reports.selectByExample(filter));
        }
        if (rows.size() > 10000) throw new FoundationException(ErrorCode.REPORT_QUANTITY_CONFLICT);
        var corrections = new HashMap<Long, BigDecimal>();
        for (var row : rows)
            if (row.getReferenceReportId() != null)
                corrections.merge(row.getReferenceReportId(), row.getQuantityDelta(), BigDecimal::add);
        var remaining = quantity;
        var additions = new ArrayList<MesProductionReportEntity>();
        for (var original : rows) {
            if (!ReportRecordType.REPORT.getValue().equals(original.getRecordType())) continue;
            var balance = original.getQuantityDelta().add(corrections.getOrDefault(original.getId(), BigDecimal.ZERO));
            if (balance.signum() <= 0) continue;
            var amount = remaining.min(balance);
            var row = new MesProductionReportEntity();
            row.setExecutionId(executionId);
            row.setRoutingNodeId(nodeId);
            row.setRecordType(ReportRecordType.ADJUSTMENT.getValue());
            row.setQuantityDelta(amount.negate());
            row.setReferenceReportId(original.getId());
            row.setSourceQualityEventId(qualityEventId);
            row.setOperatorUserId(original.getOperatorUserId());
            row.setRecordedBy(SecurityUtils.getUserId());
            row.setReportTime(original.getReportTime());
            row.setWorkstationId(original.getWorkstationId());
            row.setReason(reason);
            row.setCreateTime(LocalDateTime.now());
            row.setRequestId(UUID.nameUUIDFromBytes((qualityEventId + ":" + original.getId()).getBytes(StandardCharsets.UTF_8))
                    .toString());
            row.setCommandFingerprint(fingerprint(AdminAction.ADJUST, qualityEventId, original.getId(), amount, reason));
            additions.add(row);
            remaining = remaining.subtract(amount);
            if (remaining.signum() == 0) break;
        }
        if (remaining.signum() != 0) throw new FoundationException(ErrorCode.REPORT_QUANTITY_CONFLICT);
        quantities.validateReportChange(executionId, nodeId, quantity.negate());
        if (reports.insertBatch(additions) != additions.size())
            throw new FoundationException(ErrorCode.REPORT_QUANTITY_CONFLICT);
        publisher.publishEvent(new ProductionCompletionRequested(FactoryContext.required(), executionId));
    }

    private MesProductionReportEntity lockedOriginal(Long id) {
        var before = require(id);
        locks.execution(before.getExecutionId());
        var original = require(id);
        if (!ReportRecordType.REPORT.getValue().equals(original.getRecordType()))
            throw new FoundationException(ErrorCode.REPORT_REFERENCE_INVALID);
        return original;
    }

    private MesProductionReportEntity append(MesProductionReportEntity row, String requestId, String hash) {
        row.setRequestId(requestId);
        row.setCommandFingerprint(hash);
        row.setRecordedBy(SecurityUtils.getUserId());
        row.setCreateTime(LocalDateTime.now());
        try {
            if (reports.insert(row) != 1) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        } catch (DuplicateKeyException collision) {
            throw new FoundationException(ErrorCode.REPORT_REQUEST_CONFLICT, collision);
        }
        publisher.publishEvent(new ProductionCompletionRequested(FactoryContext.required(), row.getExecutionId()));
        return reports.selectByPrimaryKey(row.getId());
    }

    private MesProductionReportEntity replay(String requestId, String hash) {
        if (requestId == null || !requestId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw new FoundationException(ErrorCode.REPORT_REQUEST_ID_REQUIRED);
        var row = reports.selectByRequestId(requestId);
        if (row != null && (!Objects.equals(row.getCommandFingerprint(), hash)
                || !Objects.equals(row.getRecordedBy(), SecurityUtils.getUserId())))
            throw new FoundationException(ErrorCode.REPORT_REQUEST_CONFLICT);
        return row;
    }

    private static String fingerprint(Object... fields) {
        // Length-prefixed canonical values prevent delimiter ambiguity; decimals compare by numeric value.
        StringBuilder input = new StringBuilder();
        for (Object field : fields) {
            String value = Optional.ofNullable(field).map(nullableValue -> nullableValue instanceof BigDecimal number
                    ? number.stripTrailingZeros().toPlainString() : nullableValue.toString().trim()).orElse(null);
            input.append(value == null ? -1 : value.length()).append(':');
            if (value != null) input.append(value);
        }
        input.append('|').append(SecurityUtils.getUserId());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(input.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
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
        quantities.requireReadableExecutionNode(executionId, nodeId);
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
        ParameterChecks.paging(offset, limit);
        f.setOrderByClause("id desc");
        List<MesProductionReportEntity> rows;
        long total;
        try (var page = PageHelper.offsetPage(offset, limit)) {
            rows = List.copyOf(reports.selectByExample(f));
            total = page.getTotal();
        }
        return new ReportPage(rows, total, offset, limit, names(rows));
    }

    private Map<Long, String> names(List<MesProductionReportEntity> rows) {
        return references.userLabels(rows.stream()
                        .flatMap(row -> Stream.of(row.getOperatorUserId(), row.getRecordedBy())).toList()).values().stream()
                .filter(label -> label.name() != null && !label.name().isBlank())
                .collect(Collectors.toMap(label -> label.id(), label -> label.name()));
    }

    private MesProductionReportEntity require(Long id) {
        var row = reports.selectByPrimaryKey(id);
        if (row == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        return row;
    }

    private static void reason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
    }

    private static void positive(BigDecimal value) {
        delta(value);
        if (value.signum() < 0) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
    }

    private static void delta(BigDecimal value) {
        if (value == null || value.signum() == 0 || value.scale() > 6 || value.precision() - value.scale() > 14)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST);
    }

}
