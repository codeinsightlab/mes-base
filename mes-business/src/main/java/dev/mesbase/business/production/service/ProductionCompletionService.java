package dev.mesbase.business.production.service;


import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.business.production.dao.MesExecutionCompletionMapper;
import dev.mesbase.business.production.dao.MesProductionReportMapper;
import dev.mesbase.business.production.domain.MesProductionReportExample;
import dev.mesbase.framework.sync.SyncCoordinator;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.domain.MesExecutionCompletionEntity;
import dev.mesbase.business.production.domain.MesExecutionCompletionExample;
import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.production.domain.MesProductionExecutionResource;
import dev.mesbase.business.production.domain.ProductionClosureRules;
import dev.mesbase.business.production.domain.ProductionCompletionBalance;
import dev.mesbase.business.production.domain.WorkOrderCompletionRequested;
import org.springframework.context.ApplicationEventPublisher;
import dev.mesbase.business.production.domain.ProductionExecutionStatus;
import dev.mesbase.business.issue.dao.MesMaterialIssueMapper;
import dev.mesbase.business.issue.domain.MesMaterialIssueExample;
import dev.mesbase.business.issue.domain.MaterialIssueStatus;
import dev.mesbase.business.quality.service.QualityReleaseService;
import dev.mesbase.business.wm.dao.StockDocumentExtraMapper;
import dev.mesbase.business.integration.warehouse.WarehouseIntegration;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.github.pagehelper.PageHelper;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.error.ErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import static dev.mesbase.business.production.domain.ProductionClosureRules.blocked;

@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProductionCompletionService {
    private final BusinessSourceRules sources;
    private final MesProductionReportMapper reports;
    private final SyncCoordinator coordinator;

    public record Command(String requestId, String expectedBasisHash, String reason) {
    }

    public record Summary(MesProductionExecutionEntity execution, QualityReleaseService.State release,
                          MesExecutionCompletionEntity completion, boolean canComplete, String blockedReason) {
    }

    private final MesExecutionCompletionMapper completions;
    private final MesProductionExecutionMapper executions;
    private final StockDocumentExtraMapper inbound;
    private final MesMaterialIssueMapper issues;
    private final ProductionChainLocks locks;
    private final QualityReleaseService releases;
    private final ProductionQuantityService quantities;
    private final ApplicationEventPublisher events;
    private final ProductionExecutionRules rules;
    private final WarehouseIntegration integration;

    public ProductionCompletionService(MesExecutionCompletionMapper completions,
                                       MesProductionExecutionMapper executions,
                                       StockDocumentExtraMapper inbound,
                                       MesMaterialIssueMapper issues,
                                       ProductionChainLocks locks, QualityReleaseService releases,
                                       ProductionExecutionRules rules, WarehouseIntegration integration,
                                       BusinessSourceRules sources, ProductionQuantityService quantities,
                                       ApplicationEventPublisher events, MesProductionReportMapper reports,
                                       SyncCoordinator coordinator) {
        this.reports = reports;
        this.coordinator = coordinator;
        this.events = events;
        this.quantities = quantities;
        this.sources = sources;
        this.completions = completions;
        this.executions = executions;
        this.inbound = inbound;
        this.issues = issues;
        this.locks = locks;
        this.releases = releases;
        this.rules = rules;
        this.integration = integration;
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true, isolation = Isolation.READ_COMMITTED)
    public Summary summary(Long id) {
        var execution = executions.selectByPrimaryKey(id);
        if (execution == null) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        var projection = quantities.getExecutionQuantities(id);
        var q = releases.stateFor(projection);
        var f = new MesExecutionCompletionExample();
        f.createCriteria().andExecutionIdEqualTo(id);
        MesExecutionCompletionEntity completed;
        try (var page = PageHelper.offsetPage(0, 2, false)) {
            var rows = completions.selectByExample(f);
            if (rows.size() > 1) ParameterChecks.reject(ErrorCode.PRODUCTION_CLOSURE_CONFLICT);
            completed = rows.isEmpty() ? null : rows.getFirst();
        }
        var unresolved = new MesMaterialIssueExample();
        unresolved.createCriteria().andExecutionIdEqualTo(id)
                .andStatusNotIn(List.of(MaterialIssueStatus.CONFIRMED.getValue(), MaterialIssueStatus.VOIDED.getValue(), MaterialIssueStatus.SUBMIT_REJECTED.getValue()));
        String balanceReason = ProductionCompletionBalance.blockedReason(projection);
        String reason = !ProductionExecutionStatus.RUNNING.getValue()
                .equals(execution.getStatus()) ? "执行必须处于RUNNING状态" : !q.safe() ? q.blockedReason() : balanceReason != null ? balanceReason : issues.countByExample(unresolved) > 0 ? "相关领料仍有未决状态，请核实原请求" : null;
        return new Summary(execution, q, completed, reason == null, reason);
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesProductionExecutionResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public MesExecutionCompletionEntity complete(Long id, Command c) {
        if (c == null) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        ProductionClosureRules.uuid(c.requestId());
        ProductionClosureRules.reason(c.reason());
        String hash = ProductionClosureRules.hash(id, c.expectedBasisHash(), c.reason(), ProductionClosureRules.actor());
        var execution = locks.execution(id);
        var existing = completions.selectByPrimaryKey(c.requestId());
        if (existing != null) {
            if (!hash.equals(existing.getRequestHash())) ParameterChecks.reject(ErrorCode.PRODUCTION_CLOSURE_CONFLICT);
            return existing;
        }
        sources.execution(execution, null);
        if (integration.configuredMode(FactoryContext.required()) != Mode.LOCAL_ONLY)
            blocked("当前仅支持LOCAL完整完工，ERP未配置时不能假成功");
        var s = summary(id);
        if (s.completion() != null) blocked("执行已经正式完工，请查询或重放原完工请求");
        if (!s.canComplete()) blocked(s.blockedReason());
        if (c.expectedBasisHash() == null || !c.expectedBasisHash().equals(s.release().basisHash())) ParameterChecks.reject(ErrorCode.PRODUCTION_CLOSURE_CONFLICT);
        rules.checkReadyReferences(execution);
        return append(execution, s, c.requestId(), hash, ProductionClosureRules.actor(), c.reason());
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesProductionExecutionResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public boolean reconcile(Long id, SyncCoordinator.Run run) {
        assertOwner(run);
        var execution = locks.execution(id);
        if (!ProductionExecutionStatus.RUNNING.getValue().equals(execution.getStatus())) return false;
        sources.execution(execution, null);
        var s = summary(id);
        if (s.completion() != null || !s.canComplete()) return false;
        rules.checkReadyReferences(execution);
        var filter = new MesProductionReportExample();
        filter.createCriteria().andExecutionIdEqualTo(id);
        filter.setOrderByClause("id DESC");
        try (var page = PageHelper.offsetPage(0, 1, false)) {
            var initiators = reports.selectByExample(filter);
            if (initiators.isEmpty() || initiators.getFirst().getRecordedBy() == null) return false;
            var initiator = initiators.getFirst();
            String request = UUID.nameUUIDFromBytes(("auto-production-completion:" + FactoryContext.required()
                    .factoryId().value() + ":" + id).getBytes(StandardCharsets.UTF_8)).toString();
            String reason = "系统自动生产完工；发起依据报工流水 " + initiator.getId() + "，登记用户 " + initiator.getRecordedBy();
            assertOwner(run);
            append(execution, s, request, ProductionClosureRules.hash(request, s.release()
                    .basisHash(), reason), initiator.getRecordedBy(), reason);
            assertOwner(run);
            return true;
        }
    }

    private void assertOwner(SyncCoordinator.Run run) {
        if (!FactoryContext.required().equals(run.scope()) || !"workorder-completion".equals(run.syncKey()))
            throw new FoundationException(ErrorCode.SYNC_EXECUTION_LOST);
        coordinator.assertOwner(run);
    }

    private MesExecutionCompletionEntity append(MesProductionExecutionEntity execution, Summary s, String requestId,
                                                String hash, Long actor, String reason) {
        var row = new MesExecutionCompletionEntity();
        row.setId(requestId);
        row.setExecutionId(execution.getId());
        row.setEffectiveQty(s.release().effective());
        row.setReleasedQty(s.release().released());
        row.setReceiptQty(BigDecimal.ZERO);
        row.setAcceptedQty(inbound.netProductionInbound(execution.getId()));
        row.setBasisHash(s.release().basisHash());
        row.setRequestHash(hash);
        row.setOperatorUserId(actor);
        row.setRecordedAt(LocalDateTime.now());
        row.setReason(reason);
        try {
            if (completions.insert(row) != 1) ParameterChecks.reject(ErrorCode.PRODUCTION_CLOSURE_CONFLICT);
        } catch (DuplicateKeyException collision) {
            throw new FoundationException(ErrorCode.PRODUCTION_CLOSURE_CONFLICT, collision);
        }
        execution.setStatus(ProductionExecutionStatus.COMPLETED.getValue());
        execution.setEndTime(row.getRecordedAt());
        execution.setUpdateBy(row.getOperatorUserId().toString());
        execution.setUpdateTime(row.getRecordedAt());
        if (executions.updateByPrimaryKey(execution) != 1) ParameterChecks.reject(ErrorCode.PRODUCTION_CLOSURE_CONFLICT);
        events.publishEvent(new WorkOrderCompletionRequested(FactoryContext.required(), execution.getWorkorderId()));
        return row;
    }
}
