package dev.mesbase.business.quality.service;

import dev.mesbase.common.validation.ParameterChecks;
import org.apache.commons.lang3.StringUtils;
import java.util.Optional;

import dev.mesbase.business.production.domain.ProductionQuantityInput;
import dev.mesbase.business.production.domain.ProductionCompletionRequested;
import dev.mesbase.platform.execution.FactoryContext;
import org.springframework.context.ApplicationEventPublisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import dev.mesbase.business.directory.service.BusinessDirectoryService;
import dev.mesbase.business.directory.domain.DirectoryLabel;
import dev.mesbase.business.quality.dao.MesProductionDefectMapper;
import dev.mesbase.business.quality.dao.MesQualityTreatmentMapper;
import dev.mesbase.business.quality.domain.MesProductionDefectEntity;
import dev.mesbase.business.quality.domain.MesQualityTreatmentEntity;
import dev.mesbase.business.quality.domain.MesQualityTreatmentExample;
import dev.mesbase.business.quality.domain.DefectRecordAction;
import dev.mesbase.business.quality.domain.DefectDestination;
import dev.mesbase.business.quality.domain.ProductionDefectModels.NodeTotal;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Action;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.State;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Attribution;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Assessment;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Command;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Disposition;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Destination;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Summary;
import dev.mesbase.business.production.service.ProductionChainLocks;
import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.business.production.service.ExecutionPathService;
import dev.mesbase.business.production.domain.ExecutionPathStatus;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.nio.charset.StandardCharsets;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import dev.mesbase.business.quality.domain.MesProductionDefectExample;
import com.github.pagehelper.PageHelper;
import java.util.ArrayList;
import java.util.stream.Stream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
@Transactional(transactionManager = "businessTransactionManager", readOnly = true)
public class QualityTreatmentService {
    private final ApplicationEventPublisher completionPublisher;

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private final MesProductionDefectMapper defects;
    private final MesQualityTreatmentMapper events;
    private final ProductionChainLocks locks;
    private final ExecutionPathService paths;
    private final ProductionQuantityService quantities;
    private final BusinessDirectoryService directory;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    public QualityTreatmentService(MesProductionDefectMapper defects, MesQualityTreatmentMapper events,
                                   ProductionChainLocks locks, @Lazy ExecutionPathService paths,
                                   @Lazy ProductionQuantityService quantities, BusinessDirectoryService directory,
                                   ApplicationEventPublisher completionPublisher) {
        this.completionPublisher = completionPublisher;
        this.defects = defects;
        this.events = events;
        this.locks = locks;
        this.paths = paths;
        this.quantities = quantities;
        this.directory = directory;
    }

    public MesProductionDefectEntity latest(String id) {
        if (StringUtils.isBlank(id)) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        var filter = new MesProductionDefectExample();
        filter.createCriteria().andDeclarationIdEqualTo(id);
        filter.setOrderByClause("revision_no DESC");
        try (var page = PageHelper.offsetPage(0, 1)) {
            var rows = defects.selectByExample(filter);
            if (rows.isEmpty()) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
            return rows.getFirst();
        }
    }

    public record Details(Summary summary, Map<Long, DirectoryLabel> users, List<MesQualityTreatmentEntity> history) {
    }

    public record Recent(List<MesQualityTreatmentEntity> events, Map<Long, DirectoryLabel> users) {
    }

    public Recent recent() {
        var rows = List.copyOf(events.selectRecent(10));
        var users = rows.isEmpty() ? Map.<Long, DirectoryLabel>of() : directory.userLabels(rows.stream()
                .flatMap(e -> Stream.of(e.getOperatorUserId(), e.getConfirmedBy())).distinct().toList());
        return new Recent(rows, users);
    }

    public Details details(String declaration) {
        var d = latest(declaration);
        var all = executionEvents(d.getExecutionId());
        var s = summarize(d, all);
        var history = all.stream().filter(e -> e.getDeclarationId().equals(declaration)).toList();
        return new Details(s, directory.userLabels(history.stream()
                .flatMap(e -> Stream.of(e.getOperatorUserId(), e.getConfirmedBy())).distinct().toList()), history);
    }

    private List<MesQualityTreatmentEntity> executionEvents(Long execution) {
        var f = new MesQualityTreatmentExample();
        f.createCriteria().andExecutionIdEqualTo(execution);
        f.setOrderByClause("recorded_at,id");
        try (var page = PageHelper.offsetPage(0, 5001, false)) {
            var rows = events.selectByExample(f);
            if (rows.size() > 5000) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
            return rows;
        }
    }

    private List<MesProductionDefectEntity> declarations(Long execution) {
        try (var page = PageHelper.offsetPage(0, 1001, false)) {
            var rows = defects.selectLatest(execution, null, null);
            if (rows.size() > 1000) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
            return rows.stream().filter(r -> !DefectRecordAction.VOID.getValue().equals(r.getRecordAction())).toList();
        }
    }

    public MesQualityTreatmentEntity dispositionEvent(String requestId) {
        return events.selectByPrimaryKey(requestId);
    }

    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    public MesQualityTreatmentEntity recordDisposition(String declaration, Disposition command, String fingerprint,
                                                       Long stockDocumentId) {
        var d = latest(declaration);
        locks.execution(d.getExecutionId());
        var s = summary(declaration);
        if (command.destination() == null || command.expectedRevision() != d.getRevisionNo() || DefectRecordAction.VOID.getValue()
                .equals(d.getRecordAction())
                || s.attribution() != Attribution.INDEPENDENT || command.quantity() == null
                || command.quantity().signum() <= 0 || command.quantity().compareTo(s.pendingDispositionQty()) > 0)
            ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        if (command.destination() == Destination.RETURN_NODE && (stockDocumentId != null || command.warehouseId() != null)
                || command.destination() == Destination.WAREHOUSE && (stockDocumentId == null || command.warehouseId() == null))
            ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        var path = paths.view(d.getExecutionId());
        if (path.path() == null || !ExecutionPathStatus.LOCKED.getValue().equals(path.path().getPathStatus())
                || !path.selectedNodeIds().contains(d.getAffectedNodeId())) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        ProductionQuantityInput.check(command.quantity());
        ParameterChecks.text(command.reason(), true, 500, ErrorCode.QUALITY_TREATMENT_CONFLICT);
        var row = new MesQualityTreatmentEntity();
        row.setId(command.requestId());
        row.setRequestHash(fingerprint);
        row.setDeclarationId(declaration);
        row.setDeclarationRevision(d.getRevisionNo());
        row.setExecutionId(d.getExecutionId());
        row.setRoutingNodeId(d.getAffectedNodeId());
        row.setActionType(command.destination() == Destination.RETURN_NODE ? Action.RETURN_NODE.name() : Action.TRANSFER_WAREHOUSE.name());
        row.setScrapQty(ZERO);
        row.setReworkQty(command.destination() == Destination.RETURN_NODE ? command.quantity() : ZERO);
        row.setProcessedQty(ZERO);
        row.setRecoveredQty(ZERO);
        row.setFailedQty(ZERO);
        row.setTransferQty(command.destination() == Destination.WAREHOUSE ? command.quantity() : ZERO);
        row.setTargetWarehouseId(command.warehouseId());
        row.setStockDocumentId(stockDocumentId);
        row.setOperatorUserId(SecurityUtils.getUserId());
        row.setConfirmedBy(SecurityUtils.getUserId());
        row.setOccurredAt(LocalDateTime.now());
        row.setRecordedAt(row.getOccurredAt());
        row.setReason(command.reason().trim());
        try {
            if (events.insert(row) != 1) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.QUALITY_TREATMENT_CONFLICT, failure);
        }
        if (command.destination() == Destination.WAREHOUSE)
            completionPublisher.publishEvent(new ProductionCompletionRequested(FactoryContext.required(), d.getExecutionId()));
        return row;
    }

    /**
     * Read one page's processing states without per-row database queries.
     */
    public Map<String, Summary> summaries(List<MesProductionDefectEntity> declarations) {
        if (declarations.isEmpty()) return Map.of();
        if (declarations.size() > 100) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        var filter = new MesQualityTreatmentExample();
        filter.createCriteria()
                .andDeclarationIdIn(declarations.stream().map(MesProductionDefectEntity::getDeclarationId).toList());
        filter.setOrderByClause("recorded_at,id");
        List<MesQualityTreatmentEntity> rows;
        try (var page = PageHelper.offsetPage(0, 5001, false)) {
            rows = events.selectByExample(filter);
            if (rows.size() > 5000) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        }
        var grouped = groupedEvents(rows);
        var result = new LinkedHashMap<String, Summary>();
        for (var declaration : declarations)
            result.put(declaration.getDeclarationId(), summarizeRows(declaration, grouped.getOrDefault(
                    new DeclarationVersion(declaration.getDeclarationId(), declaration.getRevisionNo()), List.of())));
        return Map.copyOf(result);
    }

    public Summary summary(String declaration) {
        var d = latest(declaration);
        return summarize(d, executionEvents(d.getExecutionId()));
    }

    private Summary summarize(MesProductionDefectEntity d, List<MesQualityTreatmentEntity> all) {
        var rows = all.stream()
                .filter(e -> e.getDeclarationId().equals(d.getDeclarationId()) && e.getDeclarationRevision()
                        .equals(d.getRevisionNo())).toList();
        return summarizeRows(d, rows);
    }

    private record DeclarationVersion(String declarationId, Integer revision) {
    }

    private static Map<DeclarationVersion, List<MesQualityTreatmentEntity>> groupedEvents(
            List<MesQualityTreatmentEntity> all) {
        var grouped = new HashMap<DeclarationVersion, List<MesQualityTreatmentEntity>>();
        for (var event : all)
            grouped.computeIfAbsent(new DeclarationVersion(event.getDeclarationId(), event.getDeclarationRevision()),
                    key -> new ArrayList<>()).add(event);
        // Preserve the original recorded_at,id encounter order and immutable Summary.events contract.
        grouped.replaceAll((key, rows) -> List.copyOf(rows));
        return grouped;
    }

    private Summary summarizeRows(MesProductionDefectEntity d, List<MesQualityTreatmentEntity> rows) {
        var assessment = rows.stream().filter(e -> Action.ASSESS.name().equals(e.getActionType())).findFirst()
                .orElse(null);
        var type = assessment == null ? Attribution.UNVERIFIED : Attribution.valueOf(assessment.getAttributionType());
        var scrap = sum(rows, MesQualityTreatmentEntity::getScrapQty);
        var rework = rows.stream()
                .filter(e -> Action.ALLOCATE.name().equals(e.getActionType()) || Action.CANCEL_ALLOC.name()
                        .equals(e.getActionType()))
                .map(MesQualityTreatmentEntity::getReworkQty).reduce(ZERO, BigDecimal::add);
        var started = rows.stream()
                .filter(e -> Action.START.name().equals(e.getActionType()) || Action.CANCEL_START.name()
                        .equals(e.getActionType()))
                .map(MesQualityTreatmentEntity::getReworkQty).reduce(ZERO, BigDecimal::add);
        var processed = sum(rows, MesQualityTreatmentEntity::getProcessedQty);
        var recovered = sum(rows, MesQualityTreatmentEntity::getRecoveredQty);
        var failed = sum(rows, MesQualityTreatmentEntity::getFailedQty);
        var returned = rows.stream().filter(e -> Action.RETURN_NODE.name().equals(e.getActionType()))
                .map(MesQualityTreatmentEntity::getReworkQty).reduce(ZERO, BigDecimal::add);
        var transferred = rows.stream().filter(e -> Action.TRANSFER_WAREHOUSE.name().equals(e.getActionType()))
                .map(MesQualityTreatmentEntity::getTransferQty).reduce(ZERO, BigDecimal::add);
        var pending = d.getDefectQty().subtract(scrap).subtract(rework).subtract(returned).subtract(transferred);
        String state;
        if (DefectRecordAction.VOID.getValue().equals(d.getRecordAction())) state = State.VOIDED.name();
        else if (type == Attribution.UNVERIFIED) state = State.UNVERIFIED.name();
        else if (type == Attribution.OVERLAP) state = State.OVERLAP.name();
        else if (pending.signum() > 0) state = State.PENDING.name();
            // Existing unfinished rework must remain visible when only the unallocated balance is redirected.
        else if (started.compareTo(processed) > 0) state = State.IN_PROGRESS.name();
        else if (rework.compareTo(started) > 0) state = State.WAITING_REWORK.name();
        else if (returned.signum() > 0 && transferred.signum() > 0) state = State.DISPOSED.name();
        else if (returned.signum() > 0) state = State.RETURNED.name();
        else if (transferred.signum() > 0) state = State.TRANSFERRED.name();
        else if (failed.signum() > 0) state = State.FAILED_SCRAPPED.name();
        else if (recovered.signum() > 0) state = State.RECOVERED.name();
        else state = State.SCRAPPED.name();
        return new Summary(d.getDeclarationId(), d.getRevisionNo(), d.getExecutionId(), d.getAffectedNodeId(), d.getDefectQty(), type, Optional.ofNullable(assessment).map(nullableValue -> nullableValue.getRelatedDeclarationId()).orElse(null), Optional.ofNullable(assessment).map(nullableValue -> nullableValue.getBasis()).orElse(null), scrap, rework, started, processed, recovered, failed, pending, rework.subtract(started), started.subtract(processed), type == Attribution.INDEPENDENT ? d.getDefectQty()
                .subtract(recovered)
                .subtract(returned) : ZERO, state, d.getRecordAction(), d.getDestinationType(), rows, returned, transferred);
    }

    private static BigDecimal sum(List<MesQualityTreatmentEntity> rows,
                                  Function<MesQualityTreatmentEntity, BigDecimal> f) {
        return rows.stream().map(f).reduce(ZERO, BigDecimal::add);
    }

    public record ReleaseBasis(List<Summary> declarations, List<String> factIdentities) {
    }

    /**
     * Bounded batch reads, reused by release and completion; no per-declaration queries.
     */
    public ReleaseBasis releaseBasis(Long execution) {
        var all = executionEvents(execution);
        var versions = declarations(execution);
        var grouped = groupedEvents(all);
        var facts = new ArrayList<String>();
        versions.forEach(d -> facts.add("D:" + d.getDeclarationId() + ":" + d.getRevisionNo()));
        all.forEach(e -> facts.add("T:" + e.getId()));
        facts.sort(String::compareTo);
        return new ReleaseBasis(versions.stream().map(d -> summarizeRows(d,
                        grouped.getOrDefault(new DeclarationVersion(d.getDeclarationId(), d.getRevisionNo()), List.of())))
                .toList(), List.copyOf(facts));
    }

    public List<NodeTotal> nodeTotals(Long execution) {
        var all = executionEvents(execution);
        return nodeTotalsRows(declarations(execution), all);
    }

    /**
     * Bounded batch read; grouping and the single-node calculation share the existing authority.
     */
    public Map<Long, List<NodeTotal>> nodeTotalsBatch(List<Long> executions) {
        if (executions == null || executions.size() > 1000 || executions.stream().anyMatch(Objects::isNull)) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        var ids = executions.stream().distinct().toList();
        if (ids.isEmpty()) return Map.of();
        var f = new MesQualityTreatmentExample();
        f.createCriteria().andExecutionIdIn(ids);
        f.setOrderByClause("recorded_at,id");
        List<MesQualityTreatmentEntity> all;
        try (var page = PageHelper.offsetPage(0, 5001, false)) {
            all = events.selectByExample(f);
            if (all.size() > 5000) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        }
        List<MesProductionDefectEntity> versions;
        try (var page = PageHelper.offsetPage(0, 1001, false)) {
            versions = defects.selectLatestBatch(ids);
            if (versions.size() > 1000) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        }
        var byExecution = all.stream().collect(Collectors.groupingBy(MesQualityTreatmentEntity::getExecutionId));
        var declarations = versions.stream()
                .filter(r -> !DefectRecordAction.VOID.getValue().equals(r.getRecordAction()))
                .collect(Collectors.groupingBy(MesProductionDefectEntity::getExecutionId));
        var result = new LinkedHashMap<Long, List<NodeTotal>>();
        for (Long id : ids)
            result.put(id, nodeTotalsRows(declarations.getOrDefault(id, List.of()), byExecution.getOrDefault(id, List.of())));
        return Map.copyOf(result);
    }

    private List<NodeTotal> nodeTotalsRows(List<MesProductionDefectEntity> versions,
                                           List<MesQualityTreatmentEntity> all) {
        var totals = new HashMap<Long, BigDecimal[]>();
        var grouped = groupedEvents(all);
        for (var d : versions) {
            var s = summarizeRows(d, grouped.getOrDefault(new DeclarationVersion(d.getDeclarationId(), d.getRevisionNo()), List.of()));
            var v = totals.computeIfAbsent(d.getAffectedNodeId(), n -> new BigDecimal[]{ZERO, ZERO, ZERO, ZERO});
            v[0] = v[0].add(d.getDefectQty());
            if (s.attribution() == Attribution.INDEPENDENT) {
                v[3] = v[3].add(d.getDefectQty().subtract(s.returnedQty()));
                v[1] = v[1].add(s.recoveredQty());
            } else if (s.attribution() == Attribution.UNVERIFIED) v[2] = v[2].add(d.getDefectQty());
        }
        return totals.entrySet().stream()
                .map(e -> new NodeTotal(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3]))
                .toList();
    }

    void assertMutable(MesProductionDefectEntity before) {
        if (before == null) return;
        var all = executionEvents(before.getExecutionId());
        if (all.stream().anyMatch(e -> e.getDeclarationId().equals(before.getDeclarationId()) && !Action.ASSESS.name()
                .equals(e.getActionType()))) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        var versions = declarations(before.getExecutionId());
        var grouped = groupedEvents(all);
        for (var d : versions) {
            var s = summarizeRows(d, grouped.getOrDefault(new DeclarationVersion(d.getDeclarationId(), d.getRevisionNo()), List.of()));
            if (s.attribution() == Attribution.OVERLAP && before.getDeclarationId().equals(s.relatedDeclarationId()))
                ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        }
    }

    /**
     * Called by defect service inside its existing locked write transaction.
     */
    void assessCreatedVersion(MesProductionDefectEntity d, Assessment a) {
        if (a == null || a.type() == null || a.type() == Attribution.UNVERIFIED) return;
        var c = new Command(UUID.randomUUID()
                .toString(), d.getRevisionNo(), Action.ASSESS, null, ZERO, ZERO, ZERO, ZERO, ZERO, LocalDateTime.now(), a.basis(), a);
        append(d, c, hash(c));
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public Details execute(String declaration, Command input) {
        if (input == null || input.action() == null || input.requestId() == null) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        try {
            if (!UUID.fromString(input.requestId()).toString().equals(input.requestId())) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        } catch (IllegalArgumentException e) {
            ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        }
        var c = new Command(input.requestId(), input.expectedRevision(), input.action(), input.parentId(), number(input.scrapQty()), number(input.reworkQty()), number(input.processedQty()), number(input.recoveredQty()), number(input.failedQty()), input.occurredAt(), StringUtils.trim(input.reason()), input.assessment());
        var d = latest(declaration);
        locks.execution(d.getExecutionId());
        d = latest(declaration);
        var fingerprint = hash(c) + ":" + declaration;
        fingerprint = digest(fingerprint);
        var retry = events.selectByPrimaryKey(c.requestId());
        if (retry != null) {
            if (!fingerprint.equals(retry.getRequestHash()) || !declaration.equals(retry.getDeclarationId())) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
            return details(declaration);
        }
        if (c.expectedRevision() != d.getRevisionNo() || DefectRecordAction.VOID.getValue().equals(d.getRecordAction()))
            ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        var path = paths.view(d.getExecutionId());
        if (path.path() == null || !ExecutionPathStatus.LOCKED.getValue()
                .equals(path.path().getPathStatus()) || !path.selectedNodeIds().contains(d.getAffectedNodeId())) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        // Keep the frozen command fingerprint stable for retries, including older pending commands.
        // New facts use the server clock; client-supplied operation times are never persisted.
        c = new Command(c.requestId(), c.expectedRevision(), c.action(), c.parentId(), c.scrapQty(),
                c.reworkQty(), c.processedQty(), c.recoveredQty(), c.failedQty(), LocalDateTime.now(),
                c.reason(), c.assessment());
        append(d, c, fingerprint);
        for (var value : List.of(c.scrapQty(), c.reworkQty(), c.processedQty(), c.recoveredQty(), c.failedQty()))
            ProductionQuantityInput.check(value);
        quantities.validateDefectChange(d.getExecutionId(), d.getAffectedNodeId(), ZERO);
        completionPublisher.publishEvent(new ProductionCompletionRequested(FactoryContext.required(), d.getExecutionId()));
        return details(declaration);
    }

    private void append(MesProductionDefectEntity d, Command c, String fingerprint) {
        if (StringUtils.isBlank(c.reason()) || c.reason()
                .length() > 500 || c.occurredAt() == null || c.occurredAt().isAfter(LocalDateTime.now())) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        var s = summary(d.getDeclarationId());
        if (c.action() == Action.ASSESS) {
            var a = c.assessment();
            if (a == null || a.type() == null || a.type() == Attribution.UNVERIFIED || s.attribution() != Attribution.UNVERIFIED || s.events()
                    .stream().anyMatch(e -> Action.ASSESS.name().equals(e.getActionType()))) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
            ParameterChecks.text(a.basis(), true, 500, ErrorCode.QUALITY_TREATMENT_CONFLICT);
            if (a.type() == Attribution.OVERLAP) {
                if (a.relatedDeclarationId() == null || a.relatedDeclarationId().equals(d.getDeclarationId())) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
                var related = latest(a.relatedDeclarationId());
                var rs = summary(related.getDeclarationId());
                if (!related.getExecutionId().equals(d.getExecutionId()) || DefectRecordAction.VOID.getValue()
                        .equals(related.getRecordAction()) || rs.attribution() != Attribution.INDEPENDENT || d.getDefectQty()
                        .compareTo(related.getDefectQty()) > 0) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
            } else if (a.relatedDeclarationId() != null) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
            if (c.scrapQty().signum() != 0 || c.reworkQty().signum() != 0 || c.processedQty()
                    .signum() != 0 || c.recoveredQty().signum() != 0 || c.failedQty()
                    .signum() != 0 || c.parentId() != null) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        } else {
            if (c.assessment() != null || s.attribution() != Attribution.INDEPENDENT || !DefectDestination.SAME_NODE.getValue()
                    .equals(d.getDestinationType())) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
            var parent = Optional.ofNullable(c.parentId()).map(nullableValue -> events.selectByPrimaryKey(nullableValue)).orElse(null);
            if (parent != null && c.occurredAt().isBefore(parent.getOccurredAt())) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
            if (parent != null && (!parent.getDeclarationId()
                    .equals(d.getDeclarationId()) || !parent.getDeclarationRevision().equals(d.getRevisionNo())))
                ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
            if (c.action() == Action.ALLOCATE) {
                if (c.parentId() != null || c.processedQty().signum() != 0 || c.recoveredQty()
                        .signum() != 0 || c.failedQty().signum() != 0 || c.scrapQty().add(c.reworkQty())
                        .signum() <= 0 || c.scrapQty().add(c.reworkQty()).compareTo(s.pendingDispositionQty()) > 0)
                    ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
            } else if (c.action() == Action.START) {
                if (parent == null || !Action.ALLOCATE.name().equals(parent.getActionType()) || c.reworkQty()
                        .signum() <= 0 || c.scrapQty().signum() != 0 || c.processedQty()
                        .signum() != 0 || c.recoveredQty().signum() != 0 || c.failedQty().signum() != 0) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
                var used = s.events().stream().filter(e -> parent.getId().equals(e.getParentId()) && Action.START.name()
                                .equals(e.getActionType()))
                        .map(e -> effective(s.events(), e, MesQualityTreatmentEntity::getReworkQty))
                        .reduce(ZERO, BigDecimal::add);
                if (used.add(c.reworkQty())
                        .compareTo(effective(s.events(), parent, MesQualityTreatmentEntity::getReworkQty)) > 0) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
            } else if (c.action() == Action.RESULT) {
                if (parent == null || !Action.START.name().equals(parent.getActionType()) || c.processedQty()
                        .signum() <= 0 || c.scrapQty().signum() != 0 || c.reworkQty().signum() != 0 || c.recoveredQty()
                        .add(c.failedQty()).compareTo(c.processedQty()) != 0) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
                var used = s.events().stream()
                        .filter(e -> parent.getId().equals(e.getParentId()) && Action.RESULT.name()
                                .equals(e.getActionType()))
                        .map(e -> effective(s.events(), e, MesQualityTreatmentEntity::getProcessedQty))
                        .reduce(ZERO, BigDecimal::add);
                if (used.add(c.processedQty())
                        .compareTo(effective(s.events(), parent, MesQualityTreatmentEntity::getReworkQty)) > 0) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
            } else if (c.action() == Action.CANCEL_ALLOC) {
                if (parent == null || !Action.ALLOCATE.name().equals(parent.getActionType()) ||
                        c.processedQty().signum() != 0 || c.recoveredQty().signum() != 0 || c.failedQty()
                        .signum() != 0 ||
                        c.scrapQty().add(c.reworkQty()).signum() <= 0) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
                var spent = s.events().stream()
                        .filter(e -> parent.getId().equals(e.getParentId()) && Action.START.name()
                                .equals(e.getActionType()))
                        .map(e -> effective(s.events(), e, MesQualityTreatmentEntity::getReworkQty))
                        .reduce(ZERO, BigDecimal::add);
                if (c.scrapQty().compareTo(effective(s.events(), parent, MesQualityTreatmentEntity::getScrapQty)) > 0 ||
                        c.reworkQty().add(spent)
                                .compareTo(effective(s.events(), parent, MesQualityTreatmentEntity::getReworkQty)) > 0)
                    ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
            } else if (c.action() == Action.CANCEL_START) {
                if (parent == null || !Action.START.name().equals(parent.getActionType()) || c.reworkQty()
                        .signum() <= 0 ||
                        c.scrapQty().signum() != 0 || c.processedQty().signum() != 0 || c.recoveredQty()
                        .signum() != 0 || c.failedQty().signum() != 0) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
                var spent = s.events().stream()
                        .filter(e -> parent.getId().equals(e.getParentId()) && Action.RESULT.name()
                                .equals(e.getActionType()))
                        .map(e -> effective(s.events(), e, MesQualityTreatmentEntity::getProcessedQty))
                        .reduce(ZERO, BigDecimal::add);
                if (c.reworkQty().add(spent)
                        .compareTo(effective(s.events(), parent, MesQualityTreatmentEntity::getReworkQty)) > 0) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
            } else if (c.action() == Action.CORRECT_RESULT) {
                if (parent == null || !Action.RESULT.name().equals(parent.getActionType()) ||
                        c.scrapQty().signum() != 0 || c.reworkQty().signum() != 0 ||
                        c.recoveredQty().add(c.failedQty()).compareTo(c.processedQty()) != 0 ||
                        c.processedQty()
                                .compareTo(effective(s.events(), parent, MesQualityTreatmentEntity::getProcessedQty)) > 0)
                    ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
                if (c.processedQty()
                        .compareTo(effective(s.events(), parent, MesQualityTreatmentEntity::getProcessedQty)) == 0 &&
                        c.recoveredQty()
                                .compareTo(effective(s.events(), parent, MesQualityTreatmentEntity::getRecoveredQty)) == 0)
                    ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
            } else ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        }
        var row = new MesQualityTreatmentEntity();
        row.setId(c.requestId());
        row.setRequestHash(fingerprint);
        row.setDeclarationId(d.getDeclarationId());
        row.setDeclarationRevision(d.getRevisionNo());
        row.setExecutionId(d.getExecutionId());
        row.setRoutingNodeId(d.getAffectedNodeId());
        row.setActionType(c.action().name());
        row.setParentId(c.parentId());
        if (c.assessment() != null) {
            row.setAttributionType(c.assessment().type().name());
            row.setRelatedDeclarationId(c.assessment().relatedDeclarationId());
            row.setBasis(c.assessment().basis());
        }
        row.setTransferQty(ZERO);
        row.setScrapQty(c.scrapQty());
        row.setReworkQty(c.reworkQty());
        row.setProcessedQty(c.processedQty());
        row.setRecoveredQty(c.recoveredQty());
        row.setFailedQty(c.failedQty());
        var parent = Optional.ofNullable(c.parentId()).map(nullableValue -> events.selectByPrimaryKey(nullableValue)).orElse(null);
        if (c.action() == Action.CANCEL_ALLOC) {
            row.setScrapQty(c.scrapQty().negate());
            row.setReworkQty(c.reworkQty().negate());
        } else if (c.action() == Action.CANCEL_START) row.setReworkQty(c.reworkQty().negate());
        else if (c.action() == Action.CORRECT_RESULT) {
            // The command specifies replacement effective quantities; history stores only its signed correction.
            row.setProcessedQty(c.processedQty()
                    .subtract(effective(s.events(), parent, MesQualityTreatmentEntity::getProcessedQty)));
            row.setRecoveredQty(c.recoveredQty()
                    .subtract(effective(s.events(), parent, MesQualityTreatmentEntity::getRecoveredQty)));
            row.setFailedQty(c.failedQty()
                    .subtract(effective(s.events(), parent, MesQualityTreatmentEntity::getFailedQty)));
        }
        row.setOperatorUserId((c.action() == Action.RESULT || c.action() == Action.CORRECT_RESULT) ? parent.getOperatorUserId() : SecurityUtils.getUserId());
        row.setConfirmedBy(SecurityUtils.getUserId());
        row.setOccurredAt(c.occurredAt());
        row.setRecordedAt(LocalDateTime.now());
        row.setReason(c.reason());
        try {
            if (events.insert(row) != 1) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        } catch (DuplicateKeyException e) {
            throw new FoundationException(ErrorCode.QUALITY_TREATMENT_CONFLICT, e);
        }
    }

    private static BigDecimal effective(List<MesQualityTreatmentEntity> rows, MesQualityTreatmentEntity parent,
                                        Function<MesQualityTreatmentEntity, BigDecimal> field) {
        String correction = switch (Action.valueOf(parent.getActionType())) {
            case ALLOCATE -> Action.CANCEL_ALLOC.name();
            case START -> Action.CANCEL_START.name();
            case RESULT -> Action.CORRECT_RESULT.name();
            default -> throw new FoundationException(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        };
        return field.apply(parent).add(rows.stream()
                .filter(e -> parent.getId().equals(e.getParentId()) && correction.equals(e.getActionType()))
                .map(field).reduce(ZERO, BigDecimal::add));
    }

    private String hash(Command c) {
        try {
            return digest(SecurityUtils.getUserId() + ":" + json.writeValueAsString(c));
        } catch (JsonProcessingException e) {
            throw new FoundationException(ErrorCode.BAD_REQUEST, e);
        }
    }

    private static String digest(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static BigDecimal number(BigDecimal v) {
        if (v == null) return ZERO;
        v = v.stripTrailingZeros();
        if (v.signum() < 0 || v.scale() > 6 || v.precision() - v.scale() > 14) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        return v;
    }



}
