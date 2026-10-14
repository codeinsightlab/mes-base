package dev.mesbase.business.production.service;

import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.issue.dao.MesMaterialIssueMapper;
import dev.mesbase.business.issue.domain.MesMaterialIssueExample;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.RoutingMapper;
import dev.mesbase.business.production.dao.RoutingProductMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.production.domain.MesProductionExecutionExample;
import dev.mesbase.business.production.domain.RoutingEntity;
import dev.mesbase.business.production.domain.RoutingExample;
import dev.mesbase.business.production.domain.RoutingProductExample;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.production.domain.WorkorderExample;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Rules for administrative execution maintenance, inside the caller's business transaction.
 */
@Component
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProductionExecutionRules {
    private final ProductionChainLocks locks;
    private final MesProductionExecutionMapper executions;
    private final WorkorderMapper workorders;
    private final MaterialMapper materials;
    private final RoutingMapper routings;
    private final RoutingProductMapper products;
    private final MesMaterialIssueMapper issues;

    public ProductionExecutionRules(MesProductionExecutionMapper executions, WorkorderMapper workorders,
                                    MaterialMapper materials, RoutingMapper routings,
                                    RoutingProductMapper products, MesMaterialIssueMapper issues, ProductionChainLocks locks) {
        this.locks = locks;
        this.executions = executions;
        this.workorders = workorders;
        this.materials = materials;
        this.routings = routings;
        this.products = products;
        this.issues = issues;
    }

    public MesProductionExecutionEntity lock(Long id) {
        return locks.execution(id);
    }

    public void initialize(MesProductionExecutionEntity row) {
        var order = validateReferences(row, true);
        row.setProductId(order.getProductId());
        valid("READY".equals(row.getStatus()) && row.getStartTime() == null && row.getEndTime() == null);
        validateValues(row);
        row.setExecutionNo("PENDING");
        audit(row, true);
    }

    public void updating(MesProductionExecutionEntity old, MesProductionExecutionEntity row) {
        if (!Objects.equals(old.getStatus(), row.getStatus()) || !Objects.equals(old.getStartTime(), row.getStartTime()) || !Objects.equals(old.getEndTime(), row.getEndTime())) frozen();
        // WorkOrder/product define identity at creation; routing may be corrected only before facts.
        if (!Objects.equals(old.getWorkorderId(), row.getWorkorderId())
                || !Objects.equals(old.getProductId(), row.getProductId())) frozen();
        boolean facts = started(old) || hasIssues(old.getId());
        if (facts && (!Objects.equals(old.getRoutingId(), row.getRoutingId())
                || "READY".equals(row.getStatus()) && !"READY".equals(old.getStatus())
                || old.getStartTime() != null && row.getStartTime() == null)) frozen();
        validateReferences(row, !Objects.equals(old.getRoutingId(), row.getRoutingId()) || !started(old) && started(row));
        validateValues(row);
        audit(row, false);
    }

    public void checkReadyReferences(MesProductionExecutionEntity row) {
        var order = validateReferences(row, true);
        if (!"RELEASED".equals(order.getStatus())) throw new FoundationException(ErrorCode.WORKORDER_NOT_RELEASED);
    }

    public void deleting(Long id) {
        var row = lock(id);
        if (started(row) || hasIssues(id)) throw new FoundationException(ErrorCode.EXECUTION_REFERENCED);
    }

    private boolean hasIssues(Long id) {
        var filter = new MesMaterialIssueExample();
        filter.createCriteria().andExecutionIdEqualTo(id);
        return issues.countByExample(filter) > 0;
    }

    private boolean started(MesProductionExecutionEntity row) {
        return row.getStartTime() != null || Set.of("RUNNING", "COMPLETED").contains(row.getStatus());
    }

    private WorkorderEntity validateReferences(MesProductionExecutionEntity row, boolean newBinding) {
        valid(row.getWorkorderId() != null && row.getRoutingId() != null);
        var filter = new WorkorderExample();
        filter.createCriteria().andIdEqualTo(row.getWorkorderId());
        var patch = new WorkorderEntity();
        patch.setId(row.getWorkorderId());
        valid(workorders.updateByExampleSelective(patch, filter) == 1);
        var order = workorders.selectByPrimaryKey(row.getWorkorderId());
        valid(order != null && (row.getProductId() == null || Objects.equals(row.getProductId(), order.getProductId())));
        var product = materials.selectByPrimaryKey(order.getProductId());
        valid(product != null && "PRODUCT".equals(product.getMaterialType()));
        if (Set.of("RUNNING", "COMPLETED").contains(row.getStatus()) || row.getStartTime() != null)
            if (!"RELEASED".equals(order.getStatus())) throw new FoundationException(ErrorCode.WORKORDER_NOT_RELEASED);
        var routingFilter = new RoutingExample();
        routingFilter.createCriteria().andIdEqualTo(row.getRoutingId());
        var routingPatch = new RoutingEntity();
        routingPatch.setId(row.getRoutingId());
        valid(routings.updateByExampleSelective(routingPatch, routingFilter) == 1);
        var routing = routings.selectByPrimaryKey(row.getRoutingId());
        valid(routing != null && routing.getPublishedAt() != null && (!newBinding || "PUBLISHED".equals(routing.getStatus())));
        var applicable = new RoutingProductExample();
        applicable.createCriteria().andRoutingIdEqualTo(row.getRoutingId()).andMaterialIdEqualTo(order.getProductId());
        valid(products.countByExample(applicable) > 0);
        return order;
    }

    private void validateValues(MesProductionExecutionEntity row) {
        valid(row.getTargetQty() != null && row.getTargetQty().signum() > 0
                && row.getTargetQty().scale() <= 6 && row.getTargetQty().precision() - row.getTargetQty()
                .scale() <= 14);
        valid(row.getStatus() != null && Set.of("READY", "RUNNING", "COMPLETED", "CANCELLED")
                .contains(row.getStatus()));
        valid(!Set.of("RUNNING", "COMPLETED").contains(row.getStatus()) || row.getStartTime() != null);
        valid(!"COMPLETED".equals(row.getStatus()) || row.getEndTime() != null);
        valid(!"READY".equals(row.getStatus()) || row.getStartTime() == null && row.getEndTime() == null);
        valid(row.getEndTime() == null || row.getStartTime() != null && !row.getEndTime().isBefore(row.getStartTime()));
    }

    private void audit(MesProductionExecutionEntity row, boolean create) {
        String user = String.valueOf(SecurityUtils.getUserId());
        var now = LocalDateTime.now();
        if (create) {
            row.setCreateBy(user);
            row.setCreateTime(now);
        }
        row.setUpdateBy(user);
        row.setUpdateTime(now);
    }

    private void valid(boolean valid) {
        if (!valid) throw new FoundationException(ErrorCode.EXECUTION_INVALID);
    }

    private void frozen() {
        throw new FoundationException(ErrorCode.EXECUTION_FROZEN);
    }
}
