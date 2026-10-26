package dev.mesbase.business.production.service;

import dev.mesbase.business.production.service.BusinessSourceRules;

import dev.mesbase.business.production.domain.WorkOrderStatus;
import dev.mesbase.business.quality.service.ExecutionQualityReferenceQuery;
import dev.mesbase.business.production.domain.RoutingStatus;
import dev.mesbase.business.production.domain.ProductionExecutionStatus;
import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.issue.dao.MesMaterialIssueMapper;
import dev.mesbase.business.issue.domain.MesMaterialIssueExample;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.RoutingMapper;
import dev.mesbase.business.production.dao.RoutingProductMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.dao.WorkOrderExtraMapper;
import dev.mesbase.business.production.dao.MesExecutionPathMapper;
import dev.mesbase.business.production.dao.MesProductionReportMapper;
import dev.mesbase.business.production.dao.MesExecutionAssignmentMapper;
import dev.mesbase.business.production.domain.MesExecutionAssignmentExample;
import dev.mesbase.business.production.dao.ProductionQuantityMapper;
import dev.mesbase.business.production.domain.MesProductionReportExample;
import dev.mesbase.business.production.dao.MesExecutionPathNodeMapper;
import dev.mesbase.business.production.domain.MesExecutionPathNodeExample;
import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.production.domain.RoutingProductExample;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.List;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Rules for administrative execution maintenance, inside the caller's business transaction.
 */
@Component
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProductionExecutionRules {
    private final WorkOrderExtraMapper allocation;
    private final BusinessSourceRules sources;
    private final ExecutionQualityReferenceQuery quality;
    private final ProductionChainLocks locks;
    private final MesProductionReportMapper reports;
    private final MesExecutionAssignmentMapper assignments;
    private final ProductionQuantityMapper quantities;
    private final MesExecutionPathMapper paths;
    private final MesExecutionPathNodeMapper pathNodes;
    private final MesProductionExecutionMapper executions;
    private final WorkorderMapper workorders;
    private final MaterialMapper materials;
    private final MaterialQuantityRules quantityRules;
    private final RoutingMapper routings;
    private final RoutingProductMapper products;
    private final MesMaterialIssueMapper issues;

    public ProductionExecutionRules(MesProductionExecutionMapper executions, WorkorderMapper workorders,
                                    MaterialMapper materials, RoutingMapper routings,
                                    RoutingProductMapper products, MesMaterialIssueMapper issues,
                                    ProductionChainLocks locks, MesExecutionPathMapper paths,
                                    MesExecutionPathNodeMapper pathNodes, MesProductionReportMapper reports,
                                    ProductionQuantityMapper quantities, ExecutionQualityReferenceQuery quality,
                                    MesExecutionAssignmentMapper assignments, BusinessSourceRules sources,
                                    WorkOrderExtraMapper allocation, MaterialQuantityRules quantityRules) {
        this.allocation = allocation;
        this.sources = sources;
        this.assignments = assignments;
        this.quality = quality;
        this.reports = reports;
        this.quantities = quantities;
        this.locks = locks;
        this.paths = paths;
        this.pathNodes = pathNodes;
        this.executions = executions;
        this.workorders = workorders;
        this.materials = materials;
        this.quantityRules = quantityRules;
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
        valid(ProductionExecutionStatus.READY.getValue()
                .equals(row.getStatus()) && row.getStartTime() == null && row.getEndTime() == null);
        quantityRules.checkForWrite(row.getTargetQty(), row.getProductId());
        validateValues(row);
        capacity(row);
        audit(row, true);
    }

    public void updating(MesProductionExecutionEntity old, MesProductionExecutionEntity row) {
        var order = workorders.selectByPrimaryKey(old.getWorkorderId());
        valid(order != null);
        BusinessSourceRules.localEditable(order.getSource());
        sources.current(order.getSource());
        valid(row.getTargetQty() != null);
        if (ProductionExecutionStatus.FULLY_RECEIVED.getValue().equals(old.getStatus())
                && (!Objects.equals(old.getRoutingId(), row.getRoutingId())
                || old.getTargetQty().compareTo(row.getTargetQty()) != 0)) frozen();
        if (!Objects.equals(old.getStatus(), row.getStatus()) || !Objects.equals(old.getStartTime(), row.getStartTime()) || !Objects.equals(old.getEndTime(), row.getEndTime()))
            frozen();
        // WorkOrder/product define identity at creation; routing may be corrected only before facts.
        if (!Objects.equals(old.getWorkorderId(), row.getWorkorderId())
                || !Objects.equals(old.getProductId(), row.getProductId())
                || !Objects.equals(old.getBatchNo(), row.getBatchNo())) frozen();
        if (Objects.equals(old.getRoutingId(), row.getRoutingId())
                && old.getTargetQty().compareTo(row.getTargetQty()) == 0) {
            audit(row, false);
            return;
        }
        if (!Objects.equals(old.getRoutingId(), row.getRoutingId()) && (paths.selectByPrimaryKey(old.getId()) != null || hasAssignments(old.getId())))
            frozen();
        boolean reportFacts = hasReports(old.getId());
        boolean facts = started(old) || hasIssues(old.getId()) || reportFacts;
        valid(row.getTargetQty() != null);
        if (facts && row.getTargetQty().compareTo(old.getTargetQty()) < 0) frozen();
        if (facts && (!Objects.equals(old.getRoutingId(), row.getRoutingId())
                || ProductionExecutionStatus.READY.getValue()
                .equals(row.getStatus()) && !ProductionExecutionStatus.READY.getValue().equals(old.getStatus())
                || old.getStartTime() != null && row.getStartTime() == null)) frozen();
        validateReferences(row, !Objects.equals(old.getRoutingId(), row.getRoutingId()) || !started(old) && started(row));
        validateValues(row);
        if (!Objects.equals(row.getProductId(), old.getProductId()) || row.getTargetQty() == null
                || old.getTargetQty() == null || row.getTargetQty().compareTo(old.getTargetQty()) != 0)
            quantityRules.checkForWrite(row.getTargetQty(), row.getProductId());
        capacity(row);
        audit(row, false);
    }

    /**
     * New confirmed input reopens processing. End time and prior completion/report/receipt facts remain untouched.
     */
    public void resumeAfterInput(MesProductionExecutionEntity row) {
        valid(ProductionExecutionStatus.FULLY_RECEIVED.getValue().equals(row.getStatus()));
        var order = locks.order(row.getWorkorderId());
        valid(WorkOrderStatus.RELEASED.getValue().equals(order.getStatus()));
        row.setStatus(ProductionExecutionStatus.RUNNING.getValue());
        audit(row, false);
        valid(executions.updateByPrimaryKey(row) == 1);
    }

    public void checkReadyReferences(MesProductionExecutionEntity row) {
        var order = validateReferences(row, true);
        if (!WorkOrderStatus.RELEASED.getValue().equals(order.getStatus()))
            throw new FoundationException(ErrorCode.WORKORDER_NOT_RELEASED);
    }

    public void deleting(Long id) {
        var row = lock(id);
        var order = workorders.selectByPrimaryKey(row.getWorkorderId());
        valid(order != null);
        BusinessSourceRules.localEditable(order.getSource());
        sources.current(order.getSource());
        if (hasIssues(id) || hasReports(id) || hasAssignments(id) || allocation.hasExecutionDependents(id))
            throw new FoundationException(ErrorCode.EXECUTION_REFERENCED);
        if (quality.hasAnyHistory(id))
            throw new FoundationException(ErrorCode.EXECUTION_QUALITY_REFERENCED);
        var path = paths.selectByPrimaryKey(id);
        if (path != null) {
            var filter = new MesExecutionPathNodeExample();
            filter.createCriteria().andExecutionIdEqualTo(id);
            pathNodes.deleteByExample(filter);
            if (paths.deleteByPrimaryKey(id) != 1) frozen();
        }
    }

    private boolean hasAssignments(Long id) {
        var filter = new MesExecutionAssignmentExample();
        filter.createCriteria().andExecutionIdEqualTo(id);
        return assignments.countByExample(filter) > 0;
    }

    private boolean hasReports(Long id) {
        var filter = new MesProductionReportExample();
        filter.createCriteria().andExecutionIdEqualTo(id);
        return reports.countByExample(filter) > 0;
    }

    private boolean hasIssues(Long id) {
        var filter = new MesMaterialIssueExample();
        filter.createCriteria().andExecutionIdEqualTo(id);
        return issues.countByExample(filter) > 0;
    }

    private boolean started(MesProductionExecutionEntity row) {
        return row.getStartTime() != null || Set.of(ProductionExecutionStatus.RUNNING.getValue(), ProductionExecutionStatus.FULLY_RECEIVED.getValue())
                .contains(row.getStatus());
    }

    private WorkorderEntity validateReferences(MesProductionExecutionEntity row, boolean newBinding) {
        valid(row.getWorkorderId() != null && row.getRoutingId() != null);
        var order = workorders.selectByPrimaryKeyForUpdate(row.getWorkorderId());
        valid(order != null);
        sources.order(row.getWorkorderId());
        valid(order != null && (row.getProductId() == null || Objects.equals(row.getProductId(), order.getProductId())));
        var product = materials.selectByPrimaryKey(order.getProductId());
        valid(product != null);
        if (Set.of(ProductionExecutionStatus.RUNNING.getValue(), ProductionExecutionStatus.FULLY_RECEIVED.getValue())
                .contains(row.getStatus()) || row.getStartTime() != null)
            if (!WorkOrderStatus.RELEASED.getValue().equals(order.getStatus()))
                throw new FoundationException(ErrorCode.WORKORDER_NOT_RELEASED);
        quantityRules.lockForWrite(List.of(order.getProductId()));
        var routing = routings.selectByPrimaryKeyForUpdate(row.getRoutingId());
        valid(routing != null && routing.getPublishedAt() != null && (!newBinding || RoutingStatus.PUBLISHED.getValue()
                .equals(routing.getStatus())));
        var applicable = new RoutingProductExample();
        applicable.createCriteria().andRoutingIdEqualTo(row.getRoutingId()).andMaterialIdEqualTo(order.getProductId());
        valid(products.countByExample(applicable) > 0);
        return order;
    }

    private void capacity(MesProductionExecutionEntity row) {
        var capacity = allocation.activeAllocation(row.getWorkorderId(), row.getId());
        valid(capacity != null && capacity.allocatedTargetQty().add(row.getTargetQty())
                .compareTo(capacity.plannedQty()) <= 0);
    }

    private void validateValues(MesProductionExecutionEntity row) {
        valid(row.getTargetQty() != null && row.getTargetQty().signum() > 0
                && row.getTargetQty().scale() <= 6 && row.getTargetQty().precision() - row.getTargetQty()
                .scale() <= 14);
        valid(row.getStatus() != null && ProductionExecutionStatus.from(row.getStatus()) != null);
        valid(!Set.of(ProductionExecutionStatus.RUNNING.getValue(), ProductionExecutionStatus.FULLY_RECEIVED.getValue())
                .contains(row.getStatus()) || row.getStartTime() != null);
        valid(!ProductionExecutionStatus.FULLY_RECEIVED.getValue().equals(row.getStatus()) || row.getEndTime() != null);
        valid(!ProductionExecutionStatus.READY.getValue()
                .equals(row.getStatus()) || row.getStartTime() == null && row.getEndTime() == null);
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
