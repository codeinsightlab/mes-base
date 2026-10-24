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
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Iterator;
import java.util.NoSuchElementException;
import dev.mesbase.common.core.page.PageData;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.CurrentFact;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.RequestKind;
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

    public record Details(Summary summary) {
    }

    public record Recent(List<MesQualityTreatmentEntity> events, Map<Long, DirectoryLabel> users) {
    }

    public Recent recent() {
        var rows = List.copyOf(events.selectRecent(10));
        var users = rows.isEmpty() ? Map.<Long, DirectoryLabel>of() : directory.userLabels(rows.stream()
                .map(MesQualityTreatmentEntity::getOperatorUserId).distinct().toList());
        return new Recent(rows, users);
    }

    /**
     * Summary responses never depend on loading display history or directory names.
     */
    public Details details(String declaration) {
        return new Details(current(declaration).summary(false));
    }

    public record EventPage(List<MesQualityTreatmentEntity> items, long total, int offset, int limit,
                            Map<Long, DirectoryLabel> users) implements PageData<MesQualityTreatmentEntity> {
    }

    public EventPage history(String declaration, int offset, int limit) {
        ParameterChecks.paging(offset, limit);
        latest(declaration);
        var filter = new MesQualityTreatmentExample();
        filter.createCriteria().andDeclarationIdEqualTo(declaration);
        filter.setOrderByClause("recorded_at DESC,id DESC");
        List<MesQualityTreatmentEntity> rows;
        long total;
        try (var page = PageHelper.offsetPage(offset, limit)) {
            rows = List.copyOf(events.selectByExample(filter));
            total = page.getTotal();
        }
        var users = rows.isEmpty() ? Map.<Long, DirectoryLabel>of() : directory.userLabels(rows.stream()
                .map(MesQualityTreatmentEntity::getOperatorUserId).distinct().toList());
        return new EventPage(rows, total, offset, limit, users);
    }

    private CurrentFact current(String declaration) {
        if (StringUtils.isBlank(declaration)) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        var rows = receiptProofs.selectCurrentFacts(null, declaration);
        if (rows.size() != 1) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        return rows.getFirst();
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
        return current(declaration).summary(true);
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
        var current = receiptProofs.selectCurrentFacts(List.of(execution), null);
        for (var fact : current) {
            var summary = fact.summary(false);
            if (summary.state() == State.UNRESOLVED) return "不良申报或历史处置数量待核实";
            if (summary.attribution() == Attribution.UNVERIFIED) return "存在待核实不良申报";
            if (summary.attribution() == Attribution.INDEPENDENT && summary.pendingDispositionQty().signum() != 0)
                return "有效不良尚未全部正式入仓";
        }
        if (receiptProofs.invalidCurrentReceipts(execution) > 0)
            return "不良入仓缺少唯一有效的正式单据、明细或库存动作";
        return null;
    }

    public record ProductionBasis(List<Summary> declarations, Iterable<String> factIdentities) {
    }

    public ProductionBasis productionBasis(Long execution) {
        var summaries = receiptProofs.selectCurrentFacts(List.of(execution), null).stream()
                .map(fact -> fact.summary(true)).toList();
        return new ProductionBasis(summaries, () -> new Iterator<>() {
            private List<String> chunk = List.of();
            private int offset;
            private int index;
            private boolean exhausted;

            @Override
            public boolean hasNext() {
                if (index < chunk.size()) return true;
                if (exhausted) return false;
                try (var page = PageHelper.offsetPage(offset, 500, false)) {
                    chunk = List.copyOf(receiptProofs.selectFactIdentities(execution));
                }
                offset += chunk.size();
                index = 0;
                exhausted = chunk.size() < 500;
                return !chunk.isEmpty();
            }

            @Override
            public String next() {
                if (!hasNext()) throw new NoSuchElementException();
                return chunk.get(index++);
            }
        });
    }

    public List<NodeTotal> nodeTotals(Long execution) {
        return nodeTotalsBatch(List.of(execution)).get(execution);
    }

    public Map<Long, List<NodeTotal>> nodeTotalsBatch(List<Long> executions) {
        if (executions == null || executions.size() > 1000 || executions.stream().anyMatch(Objects::isNull))
            ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        var ids = executions.stream().distinct().toList();
        if (ids.isEmpty()) return Map.of();
        var grouped = receiptProofs.selectCurrentFacts(ids, null).stream()
                .collect(Collectors.groupingBy(CurrentFact::executionId));
        var result = new LinkedHashMap<Long, List<NodeTotal>>();
        for (var id : ids) result.put(id, nodeTotalsRows(grouped.getOrDefault(id, List.of())));
        return Map.copyOf(result);
    }

    private List<NodeTotal> nodeTotalsRows(List<CurrentFact> facts) {
        var totals = new HashMap<Long, BigDecimal[]>();
        for (var fact : facts) {
            var s = fact.summary(true);
            var v = totals.computeIfAbsent(fact.routingNodeId(), n -> new BigDecimal[]{ZERO, ZERO, ZERO, ZERO});
            v[0] = v[0].add(fact.defectQty());
            if (s.attribution() == Attribution.INDEPENDENT) {
                v[3] = v[3].add(fact.defectQty());
                v[1] = v[1].add(s.returnedQty());
            } else if (s.attribution() == Attribution.UNVERIFIED) v[2] = v[2].add(fact.defectQty());
        }
        return totals.entrySet().stream()
                .map(e -> new NodeTotal(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3]))
                .toList();
    }

    void assertMutable(MesProductionDefectEntity before) {
        if (before == null) return;
        var filter = new MesQualityTreatmentExample();
        filter.createCriteria().andDeclarationIdEqualTo(before.getDeclarationId())
                .andActionTypeNotEqualTo(Action.ASSESS.getValue());
        if (events.countByExample(filter) > 0) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        for (var fact : receiptProofs.selectCurrentFacts(List.of(before.getExecutionId()), null)) {
            var s = fact.summary(true);
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
                || current.attribution() != Attribution.UNVERIFIED)
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

    public record RequestCheck(RequestKind kind, AssessmentCommand assessment, Disposition disposition) {
    }

    public MesQualityTreatmentEntity checkRequest(String declaration, RequestCheck command) {
        if (command == null) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        String id;
        String fingerprint;
        Action action;
        if (command.kind() == RequestKind.ASSESS && command.assessment() != null && command.disposition() == null) {
            id = command.assessment().requestId();
            fingerprint = digest(hash(command.assessment()) + ":" + declaration);
            action = Action.ASSESS;
        } else if (command.kind() == RequestKind.DISPOSE && command.disposition() != null && command.assessment() == null) {
            var disposition = command.disposition();
            if (disposition.destination() == null || disposition.quantity() == null || disposition.reason() == null)
                ParameterChecks.reject(ErrorCode.BAD_REQUEST);
            id = disposition.requestId();
            fingerprint = QualityDispositionService.hash(declaration, command.disposition());
            action = Action.TRANSFER_WAREHOUSE;
        } else throw new FoundationException(ErrorCode.BAD_REQUEST);
        try {
            if (id == null || !UUID.fromString(id).toString().equals(id)) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        } catch (IllegalArgumentException failure) {
            throw new FoundationException(ErrorCode.BAD_REQUEST, failure);
        }
        var event = events.selectByPrimaryKey(id);
        if (event == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        if (!declaration.equals(event.getDeclarationId()) || !action.getValue().equals(event.getActionType())
                || !Objects.equals(SecurityUtils.getUserId(), event.getOperatorUserId())
                || !fingerprint.equals(event.getRequestHash()))
            ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        return event;
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
