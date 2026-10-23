package dev.mesbase.business.quality.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.directory.domain.DirectoryLabel;
import dev.mesbase.business.directory.service.BusinessDirectoryService;
import dev.mesbase.business.production.domain.ExecutionPathStatus;
import dev.mesbase.business.production.domain.ProductionQuantityInput;
import dev.mesbase.business.production.service.ExecutionPathService;
import dev.mesbase.business.production.service.ProductionChainLocks;
import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.business.quality.dao.MesProductionDefectMapper;
import dev.mesbase.business.quality.dao.ProductionDefectListExtraMapper;
import dev.mesbase.business.quality.dao.MesQualityTreatmentMapper;
import dev.mesbase.business.quality.domain.DefectRecordAction;
import dev.mesbase.business.quality.domain.MesProductionDefectEntity;
import dev.mesbase.business.quality.domain.MesProductionDefectExample;
import dev.mesbase.business.quality.domain.MesQualityTreatmentEntity;
import dev.mesbase.business.quality.domain.MesQualityTreatmentExample;
import dev.mesbase.business.quality.domain.ProductionDefectModels.NodeTotal;
import dev.mesbase.business.quality.domain.ProductionDefectModels.Processing;
import dev.mesbase.business.quality.domain.QualityTreatmentModels;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Action;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Assessment;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.AssessmentCommand;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Attribution;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Destination;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Disposition;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Summary;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.State;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
@Transactional(transactionManager = "businessTransactionManager", readOnly = true)
public class QualityTreatmentService {
    private final ProductionDefectListExtraMapper receiptProofs;

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
                                   ProductionDefectListExtraMapper receiptProofs) {
        this.receiptProofs = receiptProofs;
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
        try (var page = PageHelper.offsetPage(0, 1, false)) {
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
                .map(MesQualityTreatmentEntity::getOperatorUserId).distinct().toList());
        return new Recent(rows, users);
    }

    public Details details(String declaration) {
        var d = latest(declaration);
        var all = declarationEvents(declaration);
        var current = all.stream().filter(e -> e.getDeclarationRevision().equals(d.getRevisionNo())).toList();
        var s = summarizeRows(d, current, false);
        var history = all.stream().filter(e -> e.getDeclarationId().equals(declaration)).toList();
        return new Details(s, directory.userLabels(history.stream()
                .map(MesQualityTreatmentEntity::getOperatorUserId).distinct().toList()), history);
    }

    private List<MesQualityTreatmentEntity> executionEvents(Long execution) {
        var f = new MesQualityTreatmentExample();
        f.createCriteria().andExecutionIdEqualTo(execution);
        return boundedEvents(f);
    }

    private List<MesQualityTreatmentEntity> declarationEvents(String declaration) {
        var filter = new MesQualityTreatmentExample();
        filter.createCriteria().andDeclarationIdEqualTo(declaration);
        return boundedEvents(filter);
    }

    private List<MesQualityTreatmentEntity> boundedEvents(MesQualityTreatmentExample f) {
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
        if (command.destination() != Destination.WAREHOUSE || command.expectedRevision() != d.getRevisionNo() || DefectRecordAction.VOID.getValue()
                .equals(d.getRecordAction())
                || s.attribution() != Attribution.INDEPENDENT || command.quantity() == null
                || command.quantity().signum() <= 0 || command.quantity().compareTo(s.pendingDispositionQty()) > 0)
            ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        if (stockDocumentId == null || command.warehouseId() == null)
            ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        var path = paths.view(d.getExecutionId());
        if (path.path() == null || !ExecutionPathStatus.LOCKED.getValue().equals(path.path().getPathStatus())
                || !path.selectedNodeIds().contains(d.getAffectedNodeId()))
            ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        ProductionQuantityInput.check(command.quantity());
        ParameterChecks.text(command.reason(), true, 500, ErrorCode.QUALITY_TREATMENT_CONFLICT);
        var row = new MesQualityTreatmentEntity();
        row.setId(command.requestId());
        row.setRequestHash(fingerprint);
        row.setDeclarationId(declaration);
        row.setDeclarationRevision(d.getRevisionNo());
        row.setExecutionId(d.getExecutionId());
        row.setRoutingNodeId(d.getAffectedNodeId());
        row.setActionType(Action.TRANSFER_WAREHOUSE.getValue());
        row.setReturnedQty(ZERO);
        row.setTransferQty(command.quantity());
        row.setTargetWarehouseId(command.warehouseId());
        row.setStockDocumentId(stockDocumentId);
        row.setOperatorUserId(SecurityUtils.getUserId());
        row.setRecordedAt(LocalDateTime.now());
        row.setReason(command.reason().trim());
        try {
            if (events.insert(row) != 1) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.QUALITY_TREATMENT_CONFLICT, failure);
        }
        return row;
    }

    public Summary summary(String declaration) {
        var d = latest(declaration);
        return summarize(d, declarationEvents(declaration));
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
        return summarizeRows(d, rows, true);
    }

    private Summary summarizeRows(MesProductionDefectEntity d, List<MesQualityTreatmentEntity> rows, boolean strict) {
        var assessment = rows.stream().filter(e -> Action.ASSESS.name().equals(e.getActionType())).findFirst()
                .orElse(null);
        var type = assessment == null ? Attribution.UNVERIFIED : Attribution.find(assessment.getAttributionType());
        long invalidActions = rows.stream().filter(event -> !Action.recognizes(event.getActionType())).count();
        if (strict) rows.forEach(event -> Action.from(event.getActionType()));
        var returned = rows.stream().filter(e -> Action.RETURN_NODE.name().equals(e.getActionType()))
                .map(MesQualityTreatmentEntity::getReturnedQty).reduce(ZERO, BigDecimal::add);
        var transferred = rows.stream().filter(e -> Action.TRANSFER_WAREHOUSE.name().equals(e.getActionType()))
                .map(MesQualityTreatmentEntity::getTransferQty).reduce(ZERO, BigDecimal::add);
        var processing = strict ? Processing.of(d.getRecordAction(), type, d.getDefectQty(), returned, transferred)
                : Processing.read(d.getRecordAction(), type, d.getDefectQty(), returned, transferred, invalidActions);
        BigDecimal net = null;
        if (processing.state() != State.UNRESOLVED)
            net = type == Attribution.INDEPENDENT ? d.getDefectQty().subtract(returned) : ZERO;
        return new Summary(d.getDeclarationId(), d.getRevisionNo(), d.getExecutionId(), d.getAffectedNodeId(),
                d.getDefectQty(), type, Optional.ofNullable(assessment)
                .map(MesQualityTreatmentEntity::getRelatedDeclarationId)
                .orElse(null), Optional.ofNullable(assessment).map(MesQualityTreatmentEntity::getBasis).orElse(null),
                processing.pendingDispositionQty(), net,
                processing.state(), d.getRecordAction(), rows, processing.returnedQty(), processing.transferredQty(),
                QualityTreatmentModels.receiptState(processing.state(), d.getDefectQty(), processing.transferredQty()));
    }

    /**
     * Formal quality receipts affect inbound progress, never production output or loss.
     */
    public boolean hasFormalReceipt(Long execution) {
        return receiptProofs.selectReceiptProofs(execution).stream().anyMatch(proof -> proof.validCount() == 1L);
    }

    /**
     * Called under the common inbound chain lock after all posted facts are visible.
     */
    public String receiptBlockReason(Long execution) {
        var all = executionEvents(execution);
        var current = declarations(execution);
        var grouped = groupedEvents(all);
        var proofs = receiptProofs.selectReceiptProofs(execution).stream()
                .collect(Collectors.toMap(QualityTreatmentModels.ReceiptProof::eventId,
                        QualityTreatmentModels.ReceiptProof::validCount));
        for (var d : current) {
            var rows = grouped.getOrDefault(new DeclarationVersion(d.getDeclarationId(), d.getRevisionNo()), List.of());
            var summary = summarizeRows(d, rows, false);
            if (summary.state() == State.UNRESOLVED) return "不良申报或历史处置数量待核实";
            if (summary.attribution() == Attribution.UNVERIFIED) return "存在待核实不良申报";
            if (summary.attribution() != Attribution.INDEPENDENT) continue;
            if (rows.stream().filter(e -> Action.ASSESS.name().equals(e.getActionType())).count() != 1)
                return "不良申报核实依据不唯一";
            for (var event : rows) {
                if (!Objects.equals(event.getExecutionId(), execution)
                        || !Objects.equals(event.getRoutingNodeId(), d.getAffectedNodeId()))
                    return "不良处置与生产执行或实际工序不一致";
                if (Action.TRANSFER_WAREHOUSE.name().equals(event.getActionType())
                        && proofs.getOrDefault(event.getId(), 0L) != 1L)
                    return "不良入仓缺少唯一有效的正式单据、明细或库存动作";
            }
            if (summary.pendingDispositionQty().signum() != 0) return "有效不良尚未全部正式入仓";
        }
        return null;
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
        if (executions == null || executions.size() > 1000 || executions.stream().anyMatch(Objects::isNull))
            ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
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
                v[3] = v[3].add(d.getDefectQty());
                v[1] = v[1].add(s.returnedQty());
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
        var c = new AssessmentCommand(UUID.randomUUID().toString(), d.getRevisionNo(), a.basis(), a);
        appendAssessment(d, c, hash(c));
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public Details assess(String declaration, AssessmentCommand command) {
        if (command == null || command.requestId() == null || command.assessment() == null)
            ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        try {
            if (!UUID.fromString(command.requestId()).toString().equals(command.requestId()))
                ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        } catch (IllegalArgumentException failure) {
            throw new FoundationException(ErrorCode.QUALITY_TREATMENT_CONFLICT, failure);
        }
        var d = latest(declaration);
        locks.execution(d.getExecutionId());
        d = latest(declaration);
        var fingerprint = digest(hash(command) + ":" + declaration);
        var retry = events.selectByPrimaryKey(command.requestId());
        if (retry != null) {
            if (!fingerprint.equals(retry.getRequestHash()) || !declaration.equals(retry.getDeclarationId()))
                ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
            return details(declaration);
        }
        if (command.expectedRevision() != d.getRevisionNo() || DefectRecordAction.VOID.getValue()
                .equals(d.getRecordAction()))
            ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        locks.requireActiveExecution(d.getExecutionId());
        var path = paths.view(d.getExecutionId());
        if (path.path() == null || !ExecutionPathStatus.LOCKED.getValue().equals(path.path().getPathStatus())
                || !path.selectedNodeIds().contains(d.getAffectedNodeId()))
            ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        appendAssessment(d, command, fingerprint);
        quantities.validateDefectChange(d.getExecutionId(), d.getAffectedNodeId(), ZERO);
        return details(declaration);
    }

    private void appendAssessment(MesProductionDefectEntity d, AssessmentCommand command, String fingerprint) {
        ParameterChecks.text(command.reason(), true, 500, ErrorCode.QUALITY_TREATMENT_CONFLICT);
        var assessment = command.assessment();
        var current = summary(d.getDeclarationId());
        if (assessment == null || assessment.type() == null || assessment.type() == Attribution.UNVERIFIED
                || current.attribution() != Attribution.UNVERIFIED || current.events().stream()
                .anyMatch(event -> Action.ASSESS.name().equals(event.getActionType())))
            ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        ParameterChecks.text(assessment.basis(), true, 500, ErrorCode.QUALITY_TREATMENT_CONFLICT);
        if (assessment.type() == Attribution.OVERLAP) {
            if (assessment.relatedDeclarationId() == null || assessment.relatedDeclarationId()
                    .equals(d.getDeclarationId()))
                ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
            var related = latest(assessment.relatedDeclarationId());
            var other = summary(related.getDeclarationId());
            if (!related.getExecutionId().equals(d.getExecutionId()) || DefectRecordAction.VOID.getValue()
                    .equals(related.getRecordAction())
                    || other.attribution() != Attribution.INDEPENDENT || d.getDefectQty()
                    .compareTo(related.getDefectQty()) > 0)
                ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        } else if (assessment.relatedDeclarationId() != null)
            ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        var row = new MesQualityTreatmentEntity();
        row.setId(command.requestId());
        row.setRequestHash(fingerprint);
        row.setDeclarationId(d.getDeclarationId());
        row.setDeclarationRevision(d.getRevisionNo());
        row.setExecutionId(d.getExecutionId());
        row.setRoutingNodeId(d.getAffectedNodeId());
        row.setActionType(Action.ASSESS.name());
        row.setAttributionType(assessment.type().name());
        row.setRelatedDeclarationId(assessment.relatedDeclarationId());
        row.setBasis(assessment.basis());
        row.setReturnedQty(ZERO);
        row.setTransferQty(ZERO);
        row.setOperatorUserId(SecurityUtils.getUserId());
        row.setRecordedAt(LocalDateTime.now());
        row.setReason(command.reason().trim());
        try {
            if (events.insert(row) != 1) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.QUALITY_TREATMENT_CONFLICT, failure);
        }
    }

    private String hash(AssessmentCommand command) {
        try {
            return digest(SecurityUtils.getUserId() + ":" + json.writeValueAsString(command));
        } catch (JsonProcessingException failure) {
            throw new FoundationException(ErrorCode.BAD_REQUEST, failure);
        }
    }

    private static String digest(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
