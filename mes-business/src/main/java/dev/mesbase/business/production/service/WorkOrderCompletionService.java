package dev.mesbase.business.production.service;

import com.github.pagehelper.PageHelper;
import dev.mesbase.business.wm.dao.WmStockDocumentMapper;
import dev.mesbase.business.wm.domain.StockDocumentStatus;
import dev.mesbase.business.wm.domain.WmStockDocumentEntity;
import dev.mesbase.business.wm.domain.WmStockDocumentExample;
import dev.mesbase.business.wm.domain.StockDocumentType;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.WorkOrderExtraMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.production.domain.ExecutionPathStatus;
import dev.mesbase.business.production.domain.MesProductionExecutionExample;
import dev.mesbase.business.production.domain.ProductionCompletionBalance;
import dev.mesbase.business.production.domain.ProductionExecutionStatus;
import dev.mesbase.business.production.domain.WorkOrderStatus;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.quality.service.QualityTreatmentService;
import dev.mesbase.business.shared.enums.BusinessSource;
import dev.mesbase.business.wm.dao.StockDocumentExtraMapper;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Parent closure is evaluated only inside the successful inbound approval transaction.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class WorkOrderCompletionService {
    private final WorkorderMapper orders;
    private final WorkOrderExtraMapper queries;
    private final MesProductionExecutionMapper executions;
    private final WmStockDocumentMapper issues;
    private final ProductionQuantityService quantities;
    private final QualityTreatmentService quality;
    private final BusinessSourceRules sources;
    private final StockDocumentExtraMapper inbound;

    public WorkOrderCompletionService(WorkorderMapper orders, WorkOrderExtraMapper queries,
                                      MesProductionExecutionMapper executions, WmStockDocumentMapper issues,
                                      ProductionQuantityService quantities, QualityTreatmentService quality,
                                      BusinessSourceRules sources, StockDocumentExtraMapper inbound) {
        this.orders = orders;
        this.queries = queries;
        this.executions = executions;
        this.issues = issues;
        this.quantities = quantities;
        this.quality = quality;
        this.sources = sources;
        this.inbound = inbound;
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public String blockedReason(Long id) {
        var order = orders.selectByPrimaryKey(id);
        if (order == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        if (WorkOrderStatus.COMPLETED.getValue().equals(order.getStatus())) return null;
        if (!WorkOrderStatus.RELEASED.getValue().equals(order.getStatus())
                || !BusinessSource.LOCAL.getValue().equals(order.getSource())) return "工单状态或来源不允许自动完结";
        var state = queries.completionState(id);
        if (state == null || state.activeCount() == 0) return "工单没有正式有效生产执行";
        if (!state.allCompleted()) return "工单仍有未完全入库的执行或缺少正式收尾事实";
        var allocation = queries.activeAllocation(id, null);
        if (allocation == null) return "工单执行分配依据缺失";
        int allocated = allocation.allocatedTargetQty().compareTo(allocation.plannedQty());
        if (allocated < 0) return "工单执行分配数量不足计划，不能完结";
        if (allocated > 0) return "工单执行分配数量超过计划，须核实数量异常";
        var pending = new WmStockDocumentExample();
        pending.createCriteria().andSourceWorkorderIdEqualTo(id).andDocumentTypeEqualTo(StockDocumentType.MATERIAL_ISSUE.getValue());
        if (hasPendingIssues(issues.selectByExample(pending))) return "工单仍有未决领料";
        if (queries.countUnaccountedInputs(id) > 0) return "工单仍有未归属有效执行的正式投入";
        BigDecimal total = BigDecimal.ZERO;
        long after = 0;
        while (true) {
            var filter = new MesProductionExecutionExample();
            filter.createCriteria().andWorkorderIdEqualTo(id).andIdGreaterThan(after)
                    .andStatusNotEqualTo(ProductionExecutionStatus.CANCELLED.getValue());
            filter.setOrderByClause("id ASC");
            List<MesProductionExecutionEntity> rows;
            try (var page = PageHelper.offsetPage(0, 100, false)) {
                rows = List.copyOf(executions.selectByExample(filter));
            }
            if (rows.isEmpty()) break;
            for (var execution : rows) {
                if (!ProductionExecutionStatus.FULLY_RECEIVED.getValue().equals(execution.getStatus()))
                    return "工单仍有未完全入库的执行";
                var projection = quantities.getExecutionQuantities(execution.getId());
                if (!ExecutionPathStatus.LOCKED.getValue().equals(projection.pathStatus()))
                    return "执行实际路径尚未锁定";
                if (projection.nodes().stream().anyMatch(node -> node.historicalConflict()))
                    return "执行存在历史数量超额，须核实数量异常";
                var reason = ProductionCompletionBalance.blockedReason(projection);
                if (reason != null) return reason;
                if (inbound.invalidProductionInboundLines(execution.getId()) > 0)
                    return "正式生产入库明细与库存动作不一致";
                var received = inbound.formalProductionInbound(execution.getId());
                if (received.compareTo(projection.nodes().getLast().effectiveOutputQty()) != 0)
                    return "执行正常品入库与有效产出不一致";
                reason = quality.receiptBlockReason(execution.getId());
                if (reason != null) return reason;
                total = total.add(received).add(ProductionCompletionService.defectQty(projection));
                after = execution.getId();
            }
        }
        int compared = total.compareTo(order.getPlannedQty());
        if (compared < 0) return "正常入库与有效不良合计不足工单计划，不能完结";
        if (compared > 0) return "正常入库与有效不良合计超过工单计划，须核实数量异常";
        return null;
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void afterInboundApproved(Long id) {
        // The caller already owns parent and every sibling lock; no document -> sibling lock inversion.
        var order = orders.selectByPrimaryKey(id);
        if (order == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        if (!WorkOrderStatus.RELEASED.getValue().equals(order.getStatus())) return;
        if (blockedReason(id) != null) return;
        sources.current(order.getSource());
        var patch = new WorkorderEntity();
        patch.setId(id);
        patch.setStatus(WorkOrderStatus.COMPLETED.getValue());
        patch.setRevision(order.getRevision() + 1);
        patch.setUpdateTime(LocalDateTime.now());
        if (orders.updateByPrimaryKeySelective(patch) != 1) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
    }

    private boolean hasPendingIssues(List<WmStockDocumentEntity> documents) {
        // LOCAL posting and reversal commit atomically; a draft or unknown document state still blocks closure.
        return documents.stream().anyMatch(document ->
                !StockDocumentStatus.CANCELLED.getValue().equals(document.getStatus())
                && !StockDocumentStatus.POSTED.getValue().equals(document.getStatus()));
    }
}
