package dev.mesbase.business.quality.service;

import com.github.pagehelper.PageHelper;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
import dev.mesbase.business.integration.warehouse.WarehouseIntegration;
import dev.mesbase.business.production.domain.ExecutionPathStatus;
import dev.mesbase.business.production.domain.ProductionClosureRules;
import dev.mesbase.business.production.domain.ProductionExecutionStatus;
import dev.mesbase.business.production.domain.ProductionQuantityInput;
import dev.mesbase.business.production.domain.ProductionReportModels.ExecutionQuantity;
import dev.mesbase.business.production.service.BusinessSourceRules;
import dev.mesbase.business.production.service.ProductionChainLocks;
import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.business.quality.dao.MesQualityReleaseMapper;
import dev.mesbase.business.quality.domain.MesQualityReleaseEntity;
import dev.mesbase.business.quality.domain.MesQualityReleaseExample;
import dev.mesbase.business.quality.domain.QualityReleaseAction;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Attribution;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.platform.execution.FactoryContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import static dev.mesbase.business.production.domain.ProductionClosureRules.blocked;

/**
 * Absolute execution-level authorization snapshots; a changed basis invalidates authorization, never history.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class QualityReleaseService {
    public record Command(String requestId, String expectedReleaseId, BigDecimal quantity, boolean revoke,
                          String reason) {
    }

    public record State(Long executionId, BigDecimal effective, BigDecimal released, BigDecimal historicalReleased,
                        String currentReleaseId, String basisHash, boolean basisValid, boolean safe,
                        String blockedReason) {
    }

    public record History(List<MesQualityReleaseEntity> items, long total, int offset, int limit) {
    }

    private final MesQualityReleaseMapper releases;
    private final ProductionQuantityService quantities;
    private final QualityTreatmentService treatments;
    private final ProductionChainLocks locks;
    private final WarehouseIntegration integration;
    private final BusinessSourceRules sources;

    public QualityReleaseService(MesQualityReleaseMapper releases, ProductionQuantityService quantities,
                                 QualityTreatmentService treatments, ProductionChainLocks locks,
                                 WarehouseIntegration integration, BusinessSourceRules sources) {
        this.sources = sources;
        this.releases = releases;
        this.quantities = quantities;
        this.treatments = treatments;
        this.locks = locks;
        this.integration = integration;
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true, isolation = Isolation.READ_COMMITTED)
    public State state(Long execution) {
        return stateFor(quantities.getExecutionQuantities(execution));
    }

    /**
     * Reuses an authoritative projection already read under the caller's business transaction.
     */
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true, isolation = Isolation.READ_COMMITTED)
    public State stateFor(ExecutionQuantity q) {
        Long execution = q.executionId();
        var basis = treatments.releaseBasis(execution);
        String issue = null;
        if (!ExecutionPathStatus.LOCKED.getValue().equals(q.pathStatus()) || q.nodes().isEmpty())
            issue = "实际生产路径尚未锁定";
        else if (q.confirmedInputQty().signum() <= 0 || q.reservedInputQty().signum() > 0)
            issue = "缺少正式投入或仍有未决投产意图";
        else if (q.nodes().stream().anyMatch(n -> n.unverifiedDefectQty().signum() > 0))
            issue = "存在影响放行的待核实质量观察";
        else if (basis.declarations().stream().anyMatch(s -> s.attribution() == Attribution.UNVERIFIED
                || s.attribution() == Attribution.INDEPENDENT && s.pendingDispositionQty().signum() > 0))
            issue = "存在尚未完成的不良处置";
        BigDecimal output = q.nodes().isEmpty() ? BigDecimal.ZERO : q.nodes().getLast().effectiveOutputQty();
        String hash = ProductionClosureRules.hash(q.routingId(), q.pathStatus(), q.confirmedInputQty(),
                q.nodes().stream()
                        .map(n -> List.of(n.routingNodeId(), ProductionClosureRules.number(n.reportedQty()), ProductionClosureRules.number(n.effectiveOutputQty()), ProductionClosureRules.number(n.netDefectQty()), ProductionClosureRules.number(n.unverifiedDefectQty())))
                        .toList(), basis.factIdentities());
        var latest = latest(execution);
        boolean valid = latest != null && issue == null && latest.getBasisHash()
                .equals(hash) && QualityReleaseAction.RELEASE.getValue()
                .equals(latest.getActionType()) && latest.getQuantity().compareTo(output) <= 0;
        return new State(execution, output, valid ? latest.getQuantity() : BigDecimal.ZERO, latest == null ? BigDecimal.ZERO : latest.getQuantity(), Optional.ofNullable(latest)
                .map(nullableValue -> nullableValue.getId()).orElse(null), hash, valid, issue == null, issue);
    }

    private MesQualityReleaseEntity latest(Long execution) {
        var f = new MesQualityReleaseExample();
        f.createCriteria().andExecutionIdEqualTo(execution);
        f.setOrderByClause("sequence_no DESC");
        try (var page = PageHelper.offsetPage(0, 1, false)) {
            var rows = releases.selectByExample(f);
            return rows.isEmpty() ? null : rows.getFirst();
        }
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public History history(Long execution, int offset, int limit) {
        ParameterChecks.paging(offset, limit);
        var f = new MesQualityReleaseExample();
        f.createCriteria().andExecutionIdEqualTo(execution);
        f.setOrderByClause("sequence_no DESC");
        try (var p = PageHelper.offsetPage(offset, limit)) {
            var rows = List.copyOf(releases.selectByExample(f));
            return new History(rows, p.getTotal(), offset, limit);
        }
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public MesQualityReleaseEntity execute(Long execution, Command c) {
        if (c == null) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        ProductionClosureRules.uuid(c.requestId());
        ProductionClosureRules.quantity(c.quantity(), true);
        ProductionClosureRules.reason(c.reason());
        if (c.revoke() && c.quantity().signum() != 0) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        Long actor = ProductionClosureRules.actor();
        String hash = ProductionClosureRules.hash(execution, c.requestId(), c.expectedReleaseId(), ProductionClosureRules.number(c.quantity()), c.revoke(), c.reason(), actor);
        var row = locks.execution(execution);
        var old = releases.selectByPrimaryKey(c.requestId());
        if (old != null) {
            if (!hash.equals(old.getRequestHash())) ParameterChecks.reject(ErrorCode.PRODUCTION_CLOSURE_CONFLICT);
            return old;
        }
        ProductionQuantityInput.check(c.quantity());
        if (!c.revoke()) sources.finishedOutput(row, null);
        if (integration.configuredMode(FactoryContext.required()) != Mode.LOCAL_ONLY)
            blocked("ERP模式须由真实质量集成实现，不允许回退LOCAL放行");
        var state = state(execution);
        if (!Objects.equals(c.expectedReleaseId(), state.currentReleaseId()))
            ParameterChecks.reject(ErrorCode.PRODUCTION_CLOSURE_CONFLICT);
        if (!List.of(ProductionExecutionStatus.RUNNING.getValue(), ProductionExecutionStatus.COMPLETED.getValue())
                .contains(row.getStatus()))
            blocked("仅运行或生产已完工的执行可以新增正式放行或撤回");
        if (!c.revoke() && (!state.safe() || c.quantity().compareTo(state.effective()) > 0))
            blocked(state.blockedReason() == null ? "放行数量不能超过末道有效产出" : state.blockedReason());
        if (c.revoke() && state.currentReleaseId() == null)
            ParameterChecks.reject(ErrorCode.PRODUCTION_CLOSURE_CONFLICT);
        var previous = latest(execution);
        var fact = new MesQualityReleaseEntity();
        fact.setId(c.requestId());
        fact.setExecutionId(execution);
        fact.setSequenceNo(previous == null ? 1 : previous.getSequenceNo() + 1);
        fact.setActionType(c.revoke() ? QualityReleaseAction.REVOKE.getValue() : QualityReleaseAction.RELEASE.getValue());
        fact.setQuantity(c.quantity());
        fact.setBasisHash(state.basisHash());
        fact.setReferenceId(state.currentReleaseId());
        fact.setRequestHash(hash);
        fact.setOperatorUserId(actor);
        fact.setRecordedAt(LocalDateTime.now());
        fact.setReason(c.reason());
        try {
            if (releases.insert(fact) != 1) ParameterChecks.reject(ErrorCode.PRODUCTION_CLOSURE_CONFLICT);
        } catch (DuplicateKeyException collision) {
            throw new FoundationException(ErrorCode.PRODUCTION_CLOSURE_CONFLICT, collision);
        }
        return fact;
    }
}
