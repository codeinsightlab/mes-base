package dev.mesbase.business.quality.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.directory.domain.DirectoryLabel;
import dev.mesbase.business.directory.service.BusinessDirectoryService;
import dev.mesbase.business.production.domain.ExecutionPathStatus;
import dev.mesbase.business.production.service.ExecutionPathService;
import dev.mesbase.business.production.service.MaterialQuantityRules;
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
import dev.mesbase.core.error.SafeBusinessMessage;

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
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
@Transactional(transactionManager = "businessTransactionManager", readOnly = true,
        isolation = Isolation.REPEATABLE_READ)
public class QualityTreatmentService {
    private final ProductionDefectListExtraMapper receiptProofs;

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private final MesProductionDefectMapper defects;
    private final MesQualityTreatmentMapper events;
    private final ProductionChainLocks locks;
    private final ExecutionPathService paths;
    private final ProductionQuantityService quantities;
    private final BusinessDirectoryService directory;
    private final MaterialQuantityRules quantityRules;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    public QualityTreatmentService(MesProductionDefectMapper defects, MesQualityTreatmentMapper events,
                                   ProductionChainLocks locks, @Lazy ExecutionPathService paths,
                                   @Lazy ProductionQuantityService quantities, BusinessDirectoryService directory,
                                   ProductionDefectListExtraMapper receiptProofs,
                                   MaterialQuantityRules quantityRules) {
        this.receiptProofs = receiptProofs;
        this.defects = defects;
        this.events = events;
        this.locks = locks;
        this.paths = paths;
        this.quantities = quantities;
        this.directory = directory;
        this.quantityRules = quantityRules;
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

    public record Details(Summary summary, Long productId) {
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
        var summary = current(declaration).summary(false);
        return new Details(summary, quantityRules.productIdForExecution(summary.executionCode()));
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

    public List<CurrentFact> currentFacts(List<String> executions, String declaration, List<Long> ids) {
        return new CurrentQualityFacts(receiptProofs).read(executions, declaration, ids);
    }

    public void scanCurrentFacts(List<String> executions, Consumer<List<CurrentFact>> consumer) {
        new CurrentQualityFacts(receiptProofs).scan(executions, null, null, consumer);
    }

    public List<CurrentFact> interpretFacts(List<QualityTreatmentModels.Declaration> declarations) {
        return new CurrentQualityFacts(receiptProofs).interpret(declarations);
    }

    private CurrentFact current(String declaration) {
        if (StringUtils.isBlank(declaration))
            ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("不良申报ID不能为空"));
        var rows = currentFacts(null, declaration, null);
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
        var execution = locks.execution(d.getExecutionCode());
        var s = summary(declaration);
        if (command.destination() != Destination.WAREHOUSE || command.expectedRevision() != d.getRevisionNo() || DefectRecordAction.VOID.getValue()
                .equals(d.getRecordAction())
                || s.attribution() != Attribution.INDEPENDENT || command.quantity() == null
                || command.quantity().signum() <= 0 || command.quantity().compareTo(s.pendingDispositionQty()) > 0)
            ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        if (stockDocumentId == null || command.warehouseId() == null)
            ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        var path = paths.view(d.getExecutionCode());
        if (path.path() == null || !ExecutionPathStatus.LOCKED.getValue().equals(path.path().getPathStatus())
                || !path.selectedNodeIds().contains(d.getAffectedNodeId()))
            ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        quantityRules.checkSettlementForWrite(command.quantity(), s.pendingDispositionQty(), execution.getProductId());
        ParameterChecks.text(command.reason(), true, 500, ErrorCode.QUALITY_TREATMENT_CONFLICT);
        var row = new MesQualityTreatmentEntity();
        row.setId(command.requestId());
        row.setRequestHash(fingerprint);
        row.setDeclarationId(declaration);
        row.setDeclarationRevision(d.getRevisionNo());
        row.setExecutionCode(d.getExecutionCode());
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
    public boolean hasFormalReceipt(String execution) {
        return receiptProofs.selectReceiptProofs(execution).stream().anyMatch(proof -> proof.validCount() == 1L);
    }

    /**
     * Called under the common inbound chain lock after all posted facts are visible.
     */
    public String receiptBlockReason(String execution) {
        var reason = new String[1];
        scanCurrentFacts(List.of(execution), facts -> {
            for (var fact : facts) {
                if (reason[0] != null) return;
                var summary = fact.summary(false);
                if (summary.state() == State.UNRESOLVED) reason[0] = "不良申报或历史处置数量待核实";
                else if (summary.attribution() == Attribution.UNVERIFIED) reason[0] = "存在待核实不良申报";
                else if (summary.attribution() == Attribution.INDEPENDENT && summary.pendingDispositionQty()
                        .signum() != 0)
                    reason[0] = "有效不良尚未全部正式入仓";
            }
        });
        if (reason[0] != null) return reason[0];
        if (receiptProofs.invalidCurrentReceipts(execution) > 0)
            return "不良入仓缺少唯一有效的正式单据、明细或库存动作";
        return null;
    }

    public record ProductionBasis(List<Summary> declarations, Iterable<String> factIdentities) {
    }

    public ProductionBasis productionBasis(String execution) {
        var summaries = currentFacts(List.of(execution), null, null).stream()
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

    public List<NodeTotal> nodeTotals(String execution) {
        return nodeTotalsBatch(List.of(execution)).get(execution);
    }

    public Map<String, List<NodeTotal>> nodeTotalsBatch(List<String> executions) {
        if (executions == null || executions.size() > 1000 || executions.stream().anyMatch(Objects::isNull))
            ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        var ids = executions.stream().distinct().toList();
        if (ids.isEmpty()) return Map.of();
        var totals = new LinkedHashMap<String, Map<Long, BigDecimal[]>>();
        for (var id : ids) totals.put(id, new HashMap<>());
        scanCurrentFacts(ids, facts -> {
            for (var fact : facts) {
                var summary = fact.summary(true);
                var nodes = totals.get(fact.executionCode());
                var value = nodes.computeIfAbsent(fact.routingNodeId(), node -> new BigDecimal[]{ZERO, ZERO, ZERO, ZERO});
                value[0] = value[0].add(fact.defectQty());
                if (summary.attribution() == Attribution.INDEPENDENT) {
                    value[3] = value[3].add(fact.defectQty());
                    value[1] = value[1].add(summary.returnedQty());
                } else if (summary.attribution() == Attribution.UNVERIFIED) value[2] = value[2].add(fact.defectQty());
            }
        });
        var result = new LinkedHashMap<String, List<NodeTotal>>();
        totals.forEach((execution, nodes) -> result.put(execution, nodes.entrySet().stream()
                .map(entry -> new NodeTotal(entry.getKey(), entry.getValue()[0], entry.getValue()[1],
                        entry.getValue()[2], entry.getValue()[3])).toList()));
        return Map.copyOf(result);
    }

    void assertMutable(MesProductionDefectEntity before) {
        if (before == null) return;
        var filter = new MesQualityTreatmentExample();
        filter.createCriteria().andDeclarationIdEqualTo(before.getDeclarationId())
                .andActionTypeNotEqualTo(Action.ASSESS.getValue());
        if (events.countByExample(filter) > 0) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        scanCurrentFacts(List.of(before.getExecutionCode()), facts -> {
            for (var fact : facts) {
                var summary = fact.summary(true);
                if (summary.attribution() == Attribution.OVERLAP && before.getDeclarationId()
                        .equals(summary.relatedDeclarationId()))
                    ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
            }
        });
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
        locks.execution(d.getExecutionCode());
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
        locks.requireActiveExecution(d.getExecutionCode());
        var path = paths.view(d.getExecutionCode());
        if (path.path() == null || !ExecutionPathStatus.LOCKED.getValue().equals(path.path().getPathStatus())
                || !path.selectedNodeIds().contains(d.getAffectedNodeId()))
            ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        appendAssessment(d, command, fingerprint);
        quantities.validateDefectChange(d.getExecutionCode(), d.getAffectedNodeId(), ZERO);
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
            if (!related.getExecutionCode().equals(d.getExecutionCode()) || DefectRecordAction.VOID.getValue()
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
        row.setExecutionCode(d.getExecutionCode());
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
        if (command == null)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("请求核对内容不能为空"));
        String id;
        String fingerprint;
        Action action;
        if (command.kind() == RequestKind.ASSESS && command.assessment() != null && command.disposition() == null) {
            id = command.assessment().requestId();
            fingerprint = digest(hash(command.assessment()) + ":" + declaration);
            action = Action.ASSESS;
        } else if (command.kind() == RequestKind.DISPOSE && command.disposition() != null && command.assessment() == null) {
            var disposition = command.disposition();
            if (disposition.destination() == null)
                ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("请选择质量处置去向"));
            if (disposition.quantity() == null)
                ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("请填写质量处置数量"));
            if (disposition.reason() == null)
                ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("请填写质量处置原因"));
            id = disposition.requestId();
            fingerprint = QualityDispositionService.hash(declaration, command.disposition());
            action = Action.TRANSFER_WAREHOUSE;
        } else throw new FoundationException(ErrorCode.BAD_REQUEST,
                new SafeBusinessMessage("请选择要核实的质量评估或处置请求"));
        try {
            if (id == null || !UUID.fromString(id).toString().equals(id))
                ParameterChecks.reject(ErrorCode.BAD_REQUEST,
                        new SafeBusinessMessage("请求标识格式无效，请使用标准UUID"));
        } catch (IllegalArgumentException failure) {
            throw new FoundationException(ErrorCode.BAD_REQUEST, failure,
                    new SafeBusinessMessage("请求标识格式无效，请使用标准UUID"));
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
