package dev.mesbase.business.production.service;

import com.github.pagehelper.PageHelper;
import dev.mesbase.business.issue.dao.MesMaterialIssueMapper;
import dev.mesbase.business.issue.domain.MaterialIssueStatus;
import dev.mesbase.business.issue.domain.MesMaterialIssueExample;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.WorkOrderExtraMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.domain.MesProductionExecutionExample;
import dev.mesbase.business.production.domain.ProductionCompletionBalance;
import dev.mesbase.business.production.domain.ProductionExecutionStatus;
import dev.mesbase.business.production.domain.WorkOrderStatus;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.quality.service.QualityReleaseService;
import dev.mesbase.business.shared.enums.BusinessSource;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.platform.execution.FactoryContext;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled reconciliation only; execution commands never change the parent work-order status.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class WorkOrderCompletionService {
    private final WorkorderMapper orders;
    private final WorkOrderExtraMapper queries;
    private final MesProductionExecutionMapper executions;
    private final MesMaterialIssueMapper issues;
    private final ProductionQuantityService quantities;
    private final QualityReleaseService releases;
    private final BusinessSourceRules sources;
    private final SyncCoordinator coordinator;

    public WorkOrderCompletionService(WorkorderMapper orders, WorkOrderExtraMapper queries,
                                      MesProductionExecutionMapper executions, MesMaterialIssueMapper issues,
                                      ProductionQuantityService quantities, QualityReleaseService releases,
                                      BusinessSourceRules sources, SyncCoordinator coordinator) {
        this.orders = orders;
        this.queries = queries;
        this.executions = executions;
        this.issues = issues;
        this.quantities = quantities;
        this.releases = releases;
        this.sources = sources;
        this.coordinator = coordinator;
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public List<Long> candidates(Long after) {
        return queries.completionCandidates(after, 100);
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public boolean reconcile(Long id, SyncCoordinator.Run run) {
        if (!FactoryContext.required().equals(run.scope()) || !"workorder-completion".equals(run.syncKey()))
            throw new FoundationException(ErrorCode.SYNC_EXECUTION_LOST);
        coordinator.assertOwner(run);
        // Same parent-first lock as all production commands: no execution/input/quality changes race this decision.
        var order = orders.selectByPrimaryKeyForUpdate(id);
        if (order == null || !WorkOrderStatus.RELEASED.getValue().equals(order.getStatus())
                || !BusinessSource.LOCAL.getValue().equals(order.getSource())) return false;
        sources.current(order.getSource());
        var state = queries.completionState(id);
        if (state == null || !state.allCompleted()) return false;
        var pending = new MesMaterialIssueExample();
        pending.createCriteria().andWorkorderIdEqualTo(id).andStatusNotIn(List.of(
                MaterialIssueStatus.CONFIRMED.getValue(), MaterialIssueStatus.VOIDED.getValue(),
                MaterialIssueStatus.SUBMIT_REJECTED.getValue()));
        if (issues.countByExample(pending) > 0 || queries.countUnaccountedInputs(id) > 0) return false;
        // Bounded pages; reuse the authoritative quantity and quality gates for each formal execution.
        long after = 0;
        while (true) {
            var filter = new MesProductionExecutionExample();
            filter.createCriteria().andWorkorderIdEqualTo(id).andIdGreaterThan(after)
                    .andStatusNotEqualTo(ProductionExecutionStatus.CANCELLED.getValue());
            filter.setOrderByClause("id ASC");
            try (var page = PageHelper.offsetPage(0, 100, false)) {
                var rows = executions.selectByExample(filter);
                if (rows.isEmpty()) break;
                for (var execution : rows) {
                    coordinator.assertOwner(run);
                    var projection = quantities.getExecutionQuantities(execution.getId());
                    if (ProductionCompletionBalance.blockedReason(projection) != null)
                        return false;
                    var release = releases.stateFor(projection);
                    if (!release.safe() || !release.basisValid()) return false;
                    after = execution.getId();
                }
            }
        }
        coordinator.assertOwner(run);
        var patch = new WorkorderEntity();
        patch.setId(id);
        patch.setStatus(WorkOrderStatus.COMPLETED.getValue());
        patch.setRevision(order.getRevision() + 1);
        patch.setUpdateTime(LocalDateTime.now());
        if (orders.updateByPrimaryKeySelective(patch) != 1)
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        coordinator.assertOwner(run);
        return true;
    }
}
