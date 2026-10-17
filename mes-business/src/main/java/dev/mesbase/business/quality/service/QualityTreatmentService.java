package dev.mesbase.business.quality.service;

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
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
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
                                   @Lazy ProductionQuantityService quantities, BusinessDirectoryService directory) {
        this.defects = defects;
        this.events = events;
        this.locks = locks;
        this.paths = paths;
        this.quantities = quantities;
        this.directory = directory;
    }

    public MesProductionDefectEntity latest(String id) {
        if (id == null || id.isBlank()) fail();
        var filter = new MesProductionDefectExample();
        filter.createCriteria().andDeclarationIdEqualTo(id);
        filter.setOrderByClause("revision_no DESC");
        try (var page = PageHelper.offsetPage(0, 1)) {
            var rows = defects.selectByExample(filter);
            if (rows.isEmpty()) fail();
            return rows.getFirst();
        }
    }

    public record Details(Summary summary, Map<Long, DirectoryLabel> users, List<MesQualityTreatmentEntity> history) {
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
            if (rows.size() > 5000) fail();
            return rows;
        }
    }

    private List<MesProductionDefectEntity> declarations(Long execution) {
        try (var page = PageHelper.offsetPage(0, 1001, false)) {
            var rows = defects.selectLatest(execution, null, null);
            if (rows.size() > 1000) fail();
            return rows.stream().filter(r -> !DefectRecordAction.VOID.getValue().equals(r.getRecordAction())).toList();
        }
    }

    public Summary summary(String declaration) {
        var d = latest(declaration);
        return summarize(d, executionEvents(d.getExecutionId()));
    }

    private Summary summarize(MesProductionDefectEntity d, List<MesQualityTreatmentEntity> all) {
        var rows = all.stream()
                .filter(e -> e.getDeclarationId().equals(d.getDeclarationId()) && e.getDeclarationRevision()
                        .equals(d.getRevisionNo())).toList();
        var assessment = rows.stream().filter(e -> Action.ASSESS.name().equals(e.getActionType())).findFirst()
                .orElse(null);
        var type = assessment == null ? Attribution.UNVERIFIED : Attribution.valueOf(assessment.getAttributionType());
        var scrap = sum(rows, MesQualityTreatmentEntity::getScrapQty);
        var rework = rows.stream().filter(e -> Action.ALLOCATE.name().equals(e.getActionType()))
                .map(MesQualityTreatmentEntity::getReworkQty).reduce(ZERO, BigDecimal::add);
        var started = rows.stream().filter(e -> Action.START.name().equals(e.getActionType()))
                .map(MesQualityTreatmentEntity::getReworkQty).reduce(ZERO, BigDecimal::add);
        var processed = sum(rows, MesQualityTreatmentEntity::getProcessedQty);
        var recovered = sum(rows, MesQualityTreatmentEntity::getRecoveredQty);
        var failed = sum(rows, MesQualityTreatmentEntity::getFailedQty);
        var pending = d.getDefectQty().subtract(scrap).subtract(rework);
        String state = DefectRecordAction.VOID.getValue()
                .equals(d.getRecordAction()) ? State.VOIDED.name() : type == Attribution.UNVERIFIED ? State.UNVERIFIED.name() : type == Attribution.OVERLAP ? State.OVERLAP.name() : pending.signum() > 0 ? State.PENDING.name() : started.compareTo(processed) > 0 ? State.IN_PROGRESS.name() : rework.compareTo(started) > 0 ? State.WAITING_REWORK.name() : failed.signum() > 0 ? State.FAILED_SCRAPPED.name() : recovered.signum() > 0 ? State.RECOVERED.name() : State.SCRAPPED.name();
        return new Summary(d.getDeclarationId(), d.getRevisionNo(), d.getExecutionId(), d.getAffectedNodeId(), d.getDefectQty(), type, assessment == null ? null : assessment.getRelatedDeclarationId(), assessment == null ? null : assessment.getBasis(), scrap, rework, started, processed, recovered, failed, pending, rework.subtract(started), started.subtract(processed), type == Attribution.INDEPENDENT ? d.getDefectQty()
                .subtract(recovered) : ZERO, state, d.getRecordAction(), d.getDestinationType(), rows);
    }

    private static BigDecimal sum(List<MesQualityTreatmentEntity> rows,
                                  Function<MesQualityTreatmentEntity, BigDecimal> f) {
        return rows.stream().map(f).reduce(ZERO, BigDecimal::add);
    }

    public List<NodeTotal> nodeTotals(Long execution) {
        var all = executionEvents(execution);
        var totals = new HashMap<Long, BigDecimal[]>();
        for (var d : declarations(execution)) {
            var s = summarize(d, all);
            var v = totals.computeIfAbsent(d.getAffectedNodeId(), n -> new BigDecimal[]{ZERO, ZERO, ZERO, ZERO});
            v[0] = v[0].add(d.getDefectQty());
            if (s.attribution() == Attribution.INDEPENDENT) {
                v[3] = v[3].add(d.getDefectQty());
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
                .equals(e.getActionType()))) fail();
        for (var d : declarations(before.getExecutionId())) {
            var s = summarize(d, all);
            if (s.attribution() == Attribution.OVERLAP && before.getDeclarationId().equals(s.relatedDeclarationId()))
                fail();
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
        if (input == null || input.action() == null || input.requestId() == null) fail();
        try {
            if (!UUID.fromString(input.requestId()).toString().equals(input.requestId())) fail();
        } catch (IllegalArgumentException e) {
            fail();
        }
        var c = new Command(input.requestId(), input.expectedRevision(), input.action(), input.parentId(), number(input.scrapQty()), number(input.reworkQty()), number(input.processedQty()), number(input.recoveredQty()), number(input.failedQty()), input.occurredAt(), clean(input.reason()), input.assessment());
        var d = latest(declaration);
        locks.execution(d.getExecutionId());
        d = latest(declaration);
        var fingerprint = hash(c) + ":" + declaration;
        fingerprint = digest(fingerprint);
        var retry = events.selectByPrimaryKey(c.requestId());
        if (retry != null) {
            if (!fingerprint.equals(retry.getRequestHash()) || !declaration.equals(retry.getDeclarationId())) fail();
            return details(declaration);
        }
        if (c.expectedRevision() != d.getRevisionNo() || DefectRecordAction.VOID.getValue().equals(d.getRecordAction()))
            fail();
        var path = paths.view(d.getExecutionId());
        if (path.path() == null || !ExecutionPathStatus.LOCKED.getValue()
                .equals(path.path().getPathStatus()) || !path.selectedNodeIds().contains(d.getAffectedNodeId())) fail();
        append(d, c, fingerprint);
        quantities.validateDefectChange(d.getExecutionId(), d.getAffectedNodeId(), ZERO);
        return details(declaration);
    }

    private void append(MesProductionDefectEntity d, Command c, String fingerprint) {
        if (c.reason() == null || c.reason().isBlank() || c.reason()
                .length() > 500 || c.occurredAt() == null || c.occurredAt().isAfter(LocalDateTime.now())) fail();
        var s = summary(d.getDeclarationId());
        if (c.action() == Action.ASSESS) {
            var a = c.assessment();
            if (a == null || a.type() == null || a.type() == Attribution.UNVERIFIED || s.attribution() != Attribution.UNVERIFIED || s.events()
                    .stream().anyMatch(e -> Action.ASSESS.name().equals(e.getActionType()))) fail();
            text(a.basis());
            if (a.type() == Attribution.OVERLAP) {
                if (a.relatedDeclarationId() == null || a.relatedDeclarationId().equals(d.getDeclarationId())) fail();
                var related = latest(a.relatedDeclarationId());
                var rs = summary(related.getDeclarationId());
                if (!related.getExecutionId().equals(d.getExecutionId()) || DefectRecordAction.VOID.getValue()
                        .equals(related.getRecordAction()) || rs.attribution() != Attribution.INDEPENDENT || d.getDefectQty()
                        .compareTo(related.getDefectQty()) > 0) fail();
            } else if (a.relatedDeclarationId() != null) fail();
            if (c.scrapQty().signum() != 0 || c.reworkQty().signum() != 0 || c.processedQty()
                    .signum() != 0 || c.recoveredQty().signum() != 0 || c.failedQty()
                    .signum() != 0 || c.parentId() != null) fail();
        } else {
            if (c.assessment() != null || s.attribution() != Attribution.INDEPENDENT || !DefectDestination.SAME_NODE.getValue()
                    .equals(d.getDestinationType())) fail();
            var parent = c.parentId() == null ? null : events.selectByPrimaryKey(c.parentId());
            if (parent != null && c.occurredAt().isBefore(parent.getOccurredAt())) fail();
            if (parent != null && (!parent.getDeclarationId()
                    .equals(d.getDeclarationId()) || !parent.getDeclarationRevision().equals(d.getRevisionNo())))
                fail();
            if (c.action() == Action.ALLOCATE) {
                if (c.parentId() != null || c.processedQty().signum() != 0 || c.recoveredQty()
                        .signum() != 0 || c.failedQty().signum() != 0 || c.scrapQty().add(c.reworkQty())
                        .signum() <= 0 || c.scrapQty().add(c.reworkQty()).compareTo(s.pendingDispositionQty()) > 0)
                    fail();
            } else if (c.action() == Action.START) {
                if (parent == null || !Action.ALLOCATE.name().equals(parent.getActionType()) || c.reworkQty()
                        .signum() <= 0 || c.scrapQty().signum() != 0 || c.processedQty()
                        .signum() != 0 || c.recoveredQty().signum() != 0 || c.failedQty().signum() != 0) fail();
                var used = s.events().stream().filter(e -> parent.getId().equals(e.getParentId()) && Action.START.name()
                                .equals(e.getActionType())).map(MesQualityTreatmentEntity::getReworkQty)
                        .reduce(ZERO, BigDecimal::add);
                if (used.add(c.reworkQty()).compareTo(parent.getReworkQty()) > 0) fail();
            } else if (c.action() == Action.RESULT) {
                if (parent == null || !Action.START.name().equals(parent.getActionType()) || c.processedQty()
                        .signum() <= 0 || c.scrapQty().signum() != 0 || c.reworkQty().signum() != 0 || c.recoveredQty()
                        .add(c.failedQty()).compareTo(c.processedQty()) != 0) fail();
                var used = s.events().stream()
                        .filter(e -> parent.getId().equals(e.getParentId()) && Action.RESULT.name()
                                .equals(e.getActionType())).map(MesQualityTreatmentEntity::getProcessedQty)
                        .reduce(ZERO, BigDecimal::add);
                if (used.add(c.processedQty()).compareTo(parent.getReworkQty()) > 0) fail();
            } else fail();
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
        row.setScrapQty(c.scrapQty());
        row.setReworkQty(c.reworkQty());
        row.setProcessedQty(c.processedQty());
        row.setRecoveredQty(c.recoveredQty());
        row.setFailedQty(c.failedQty());
        var parent = c.parentId() == null ? null : events.selectByPrimaryKey(c.parentId());
        row.setOperatorUserId(c.action() == Action.RESULT ? parent.getOperatorUserId() : SecurityUtils.getUserId());
        row.setConfirmedBy(SecurityUtils.getUserId());
        row.setOccurredAt(c.occurredAt());
        row.setRecordedAt(LocalDateTime.now());
        row.setReason(c.reason());
        try {
            if (events.insert(row) != 1) fail();
        } catch (DuplicateKeyException e) {
            throw new FoundationException(ErrorCode.QUALITY_TREATMENT_CONFLICT, e);
        }
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
        if (v.signum() < 0 || v.scale() > 6 || v.precision() - v.scale() > 14) fail();
        return v;
    }

    private static String clean(String v) {
        return v == null ? null : v.trim();
    }

    private static void text(String s) {
        if (s == null || s.isBlank() || s.length() > 500) fail();
    }

    private static void fail() {
        throw new FoundationException(ErrorCode.QUALITY_TREATMENT_CONFLICT);
    }
}
