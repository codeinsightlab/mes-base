package dev.mesbase.business.production.service;

import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.production.domain.WorkorderExample;
import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.production.domain.MesProductionExecutionExample;
import dev.mesbase.business.issue.dao.MesMaterialIssueMapper;
import dev.mesbase.business.issue.domain.MesMaterialIssueEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueExample;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.util.List;
import dev.mesbase.business.production.domain.WorkOrderStatus;
import java.util.ArrayList;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Current production commands share WorkOrder -> Execution -> Issue lock order.
 */
@Component
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProductionChainLocks {
    private final WorkorderMapper orders;
    private final MesProductionExecutionMapper executions;
    private final MesMaterialIssueMapper issues;

    public ProductionChainLocks(WorkorderMapper orders, MesProductionExecutionMapper executions,
                                MesMaterialIssueMapper issues) {
        this.orders = orders;
        this.executions = executions;
        this.issues = issues;
    }

    public static void requireActive(WorkorderEntity row) {
        if (row != null && WorkOrderStatus.CANCELLED.getValue().equals(row.getStatus()))
            throw new FoundationException(ErrorCode.WORKORDER_CANCELLED);
    }

    public void requireActiveExecution(Long id) {
        var row = execution(id);
        requireActive(orders.selectByPrimaryKey(row.getWorkorderId()));
    }

    public WorkorderEntity order(Long id) {
        if (id == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        var f = new WorkorderExample();
        f.createCriteria().andIdEqualTo(id);
        var patch = new WorkorderEntity();
        patch.setId(id);
        if (orders.updateByExampleSelective(patch, f) != 1) throw new FoundationException(ErrorCode.NOT_FOUND);
        return orders.selectByPrimaryKey(id);
    }

    public MesProductionExecutionEntity execution(Long id) {
        var before = executions.selectByPrimaryKey(id);
        if (before == null) throw new FoundationException(ErrorCode.EXECUTION_NOT_FOUND);
        order(before.getWorkorderId());
        var f = new MesProductionExecutionExample();
        f.createCriteria().andIdEqualTo(id);
        var patch = new MesProductionExecutionEntity();
        patch.setId(id);
        if (executions.updateByExampleSelective(patch, f) != 1)
            throw new FoundationException(ErrorCode.EXECUTION_NOT_FOUND);
        var current = executions.selectByPrimaryKey(id);
        if (!Objects.equals(before.getWorkorderId(), current.getWorkorderId()))
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        return current;
    }

    public void issues(List<Long> ids) {
        issues(ids, null, null);
    }

    public void issueUpdate(Long id, Long workorderId, Long executionId) {
        issues(List.of(id), workorderId, executionId);
    }

    private void issues(List<Long> ids, Long targetOrder, Long targetExecution) {
        if (ids.isEmpty() || ids.size() > 100) throw new FoundationException(ErrorCode.BAD_REQUEST);
        var f = new MesMaterialIssueExample();
        f.createCriteria().andIdIn(ids);
        var before = issues.selectByExample(f);
        if (before.size() != ids.stream().distinct().count()) throw new FoundationException(ErrorCode.NOT_FOUND);
        var orderIds = new ArrayList<Long>();
        var executionIds = new ArrayList<Long>();
        before.forEach(row -> {
            if (row.getWorkorderId() != null) orderIds.add(row.getWorkorderId());
            if (row.getExecutionId() != null) executionIds.add(row.getExecutionId());
        });
        if (targetOrder != null) orderIds.add(targetOrder);
        if (targetExecution != null) executionIds.add(targetExecution);
        // Bounded to at most 100 existing headers plus one proposed association.
        for (Long id : executionIds.stream().distinct().toList()) {
            var execution = executions.selectByPrimaryKey(id);
            if (execution == null) throw new FoundationException(ErrorCode.EXECUTION_NOT_FOUND);
            orderIds.add(execution.getWorkorderId());
        }
        for (Long id : orderIds.stream().distinct().sorted().toList()) order(id);
        for (Long id : executionIds.stream().distinct().sorted().toList()) execution(id);
        for (var row : before.stream().sorted((a, b) -> a.getId().compareTo(b.getId())).toList()) {
            var byId = new MesMaterialIssueExample();
            byId.createCriteria().andIdEqualTo(row.getId());
            var patch = new MesMaterialIssueEntity();
            patch.setId(row.getId());
            if (issues.updateByExampleSelective(patch, byId) != 1) throw new FoundationException(ErrorCode.NOT_FOUND);
            var current = issues.selectByPrimaryKey(row.getId());
            if (!Objects.equals(row.getWorkorderId(), current.getWorkorderId()) || !Objects.equals(row.getExecutionId(), current.getExecutionId()))
                throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }
}
