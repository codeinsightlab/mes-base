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
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Current production commands share WorkOrder -> Execution -> Issue lock order. */
@Component
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProductionChainLocks {
    private final WorkorderMapper orders;
    private final MesProductionExecutionMapper executions;
    private final MesMaterialIssueMapper issues;
    public ProductionChainLocks(WorkorderMapper orders, MesProductionExecutionMapper executions, MesMaterialIssueMapper issues) {
        this.orders = orders; this.executions = executions; this.issues = issues;
    }
    public WorkorderEntity order(Long id) {
        if (id == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        var f = new WorkorderExample(); f.createCriteria().andIdEqualTo(id);
        var patch = new WorkorderEntity(); patch.setId(id);
        if (orders.updateByExampleSelective(patch, f) != 1) throw new FoundationException(ErrorCode.NOT_FOUND);
        return orders.selectByPrimaryKey(id);
    }
    public MesProductionExecutionEntity execution(Long id) {
        var before = executions.selectByPrimaryKey(id);
        if (before == null) throw new FoundationException(ErrorCode.EXECUTION_NOT_FOUND);
        order(before.getWorkorderId());
        var f = new MesProductionExecutionExample(); f.createCriteria().andIdEqualTo(id);
        var patch = new MesProductionExecutionEntity(); patch.setId(id);
        if (executions.updateByExampleSelective(patch, f) != 1) throw new FoundationException(ErrorCode.EXECUTION_NOT_FOUND);
        var current = executions.selectByPrimaryKey(id);
        if (!Objects.equals(before.getWorkorderId(), current.getWorkorderId())) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        return current;
    }
    public void issues(List<Long> ids) {
        if (ids.isEmpty() || ids.size() > 100) throw new FoundationException(ErrorCode.BAD_REQUEST);
        var f = new MesMaterialIssueExample(); f.createCriteria().andIdIn(ids);
        var before = issues.selectByExample(f);
        if (before.size() != ids.stream().distinct().count()) throw new FoundationException(ErrorCode.NOT_FOUND);
        for (Long id : before.stream().map(MesMaterialIssueEntity::getWorkorderId).filter(Objects::nonNull).distinct().sorted().toList()) order(id);
        for (Long id : before.stream().map(MesMaterialIssueEntity::getExecutionId).filter(Objects::nonNull).distinct().sorted().toList()) execution(id);
        for (var row : before.stream().sorted((a,b) -> a.getId().compareTo(b.getId())).toList()) {
            var byId = new MesMaterialIssueExample(); byId.createCriteria().andIdEqualTo(row.getId());
            var patch = new MesMaterialIssueEntity(); patch.setId(row.getId());
            if (issues.updateByExampleSelective(patch, byId) != 1) throw new FoundationException(ErrorCode.NOT_FOUND);
            var current = issues.selectByPrimaryKey(row.getId());
            if (!Objects.equals(row.getWorkorderId(), current.getWorkorderId()) || !Objects.equals(row.getExecutionId(), current.getExecutionId())) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }
}
