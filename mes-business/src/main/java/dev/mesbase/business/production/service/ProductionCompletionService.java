package dev.mesbase.business.production.service;

import com.github.pagehelper.PageHelper;
import dev.mesbase.business.wm.dao.WmStockDocumentMapper;
import dev.mesbase.business.wm.domain.StockDocumentStatus;
import dev.mesbase.business.issue.domain.MaterialIssueSubmissionStatus;
import dev.mesbase.business.wm.domain.WmStockDocumentEntity;
import dev.mesbase.business.wm.domain.WmStockDocumentExample;
import dev.mesbase.business.wm.domain.StockDocumentType;
import dev.mesbase.business.production.dao.MesExecutionCompletionMapper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.domain.ExecutionPathStatus;
import dev.mesbase.business.production.domain.MesExecutionCompletionEntity;
import dev.mesbase.business.production.domain.MesExecutionCompletionExample;
import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.production.domain.MesProductionExecutionExample;
import dev.mesbase.business.production.domain.MesProductionExecutionResource;
import dev.mesbase.business.production.domain.ProductionClosureRules;
import dev.mesbase.business.production.domain.ProductionClosureBlockedException;
import dev.mesbase.business.production.domain.ProductionCompletionBalance;
import dev.mesbase.business.production.domain.ProductionExecutionStatus;
import dev.mesbase.business.production.domain.ProductionReceiptState;
import dev.mesbase.business.production.domain.ProductionReportModels.ExecutionQuantity;
import dev.mesbase.business.quality.service.QualityTreatmentService;
import dev.mesbase.business.wm.dao.StockDocumentExtraMapper;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inbound approval is the only creator of new execution closure facts.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProductionCompletionService {
    public record Command(String requestId, String expectedBasisHash, String reason) {
    }

    public record Summary(MesProductionExecutionEntity execution, BigDecimal effective,
                          MesExecutionCompletionEntity completion, String blockedReason,
                          BigDecimal receivedQty, BigDecimal defectQty, String receiptState,
                          String orderBlockedReason) {
    }

    private final MesExecutionCompletionMapper completions;
    private final MesProductionExecutionMapper executions;
    private final StockDocumentExtraMapper inbound;
    private final WmStockDocumentMapper issues;
    private final ProductionChainLocks locks;
    private final QualityTreatmentService quality;
    private final ProductionQuantityService quantities;
    private final ProductionExecutionRules rules;
    private final BusinessSourceRules sources;
    private final WorkOrderCompletionService workorders;

    public ProductionCompletionService(MesExecutionCompletionMapper completions,
                                       MesProductionExecutionMapper executions, StockDocumentExtraMapper inbound,
                                       WmStockDocumentMapper issues,
                                       ProductionChainLocks locks,
                                       QualityTreatmentService quality,
                                       ProductionQuantityService quantities, ProductionExecutionRules rules,
                                       BusinessSourceRules sources, WorkOrderCompletionService workorders) {
        this.completions = completions;
        this.executions = executions;
        this.inbound = inbound;
        this.issues = issues;
        this.locks = locks;
        this.quality = quality;
        this.quantities = quantities;
        this.rules = rules;
        this.sources = sources;
        this.workorders = workorders;
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true, isolation = Isolation.READ_COMMITTED)
    public Summary summary(Long id) {
        var execution = executions.selectByPrimaryKey(id);
        if (execution == null) ParameterChecks.reject(ErrorCode.NOT_FOUND);
        var projection = quantities.getExecutionQuantities(id);
        var completion = completion(id);
        var received = inbound.formalProductionInbound(id);
        var defect = defectQty(projection);
        String reason = blockedReason(execution, projection, received);
        String state = ProductionExecutionStatus.FULLY_RECEIVED.getValue().equals(execution.getStatus())
                ? ProductionReceiptState.FULLY_RECEIVED.getValue() : (received.signum() > 0 || quality.hasFormalReceipt(id))
                ? ProductionReceiptState.PARTIAL.getValue() : ProductionReceiptState.PENDING.getValue();
        return new Summary(execution, projection.nodes().isEmpty() ? BigDecimal.ZERO : projection.nodes().getLast()
                .effectiveOutputQty(), completion, reason, received, defect, state,
                workorders.blockedReason(execution.getWorkorderId()));
    }

    /**
     * Locks every sibling in ascending order before any document or inventory lock is taken.
     */
    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void lockInboundChain(Long id) {
        var before = executions.selectByPrimaryKey(id);
        if (before == null) ParameterChecks.reject(ErrorCode.NOT_FOUND);
        locks.order(before.getWorkorderId());
        long after = 0;
        while (true) {
            var filter = new MesProductionExecutionExample();
            filter.createCriteria().andWorkorderIdEqualTo(before.getWorkorderId()).andIdGreaterThan(after);
            filter.setOrderByClause("id ASC");
            List<MesProductionExecutionEntity> rows;
            try (var page = PageHelper.offsetPage(0, 100, false)) {
                rows = List.copyOf(executions.selectByExample(filter));
            }
            if (rows.isEmpty()) break;
            for (var row : rows) {
                locks.execution(row.getId());
                after = row.getId();
            }
        }
        var current = executions.selectByPrimaryKey(id);
        if (current == null || !Objects.equals(current.getWorkorderId(), before.getWorkorderId()))
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
    }

    /**
     * Caller has locked the entire chain and saved POSTED document plus its stock actions in this transaction.
     */
    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesProductionExecutionResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void afterInboundApproved(Long id, String auditRequestId) {
        ProductionClosureRules.uuid(auditRequestId);
        var execution = executions.selectByPrimaryKey(id);
        if (execution == null) ParameterChecks.reject(ErrorCode.NOT_FOUND);
        if (ProductionExecutionStatus.RUNNING.getValue().equals(execution.getStatus())) {
            sources.finishedOutput(execution, null);
            var projection = quantities.getExecutionQuantities(id);
            var received = inbound.formalProductionInbound(id);
            if (blockedReason(execution, projection, received) == null) {
                rules.checkReadyReferences(execution);
                append(execution, projection, received, auditRequestId);
            }
        }
        workorders.afterInboundApproved(execution.getWorkorderId());
    }

    /**
     * Explicitly rechecks existing posted facts after a former business blocker has been resolved.
     */
    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesProductionExecutionResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public Summary recheckReceipt(Long id) {
        lockInboundChain(id);
        var execution = executions.selectByPrimaryKey(id);
        if (execution == null) ParameterChecks.reject(ErrorCode.NOT_FOUND);
        sources.finishedOutput(execution, null);
        String auditRequestId = inbound.latestInboundAuditId(id);
        if (auditRequestId != null) {
            afterInboundApproved(id, auditRequestId);
            return summary(id);
        }
        var current = summary(id);
        return new Summary(current.execution(), current.effective(), current.completion(),
                "没有与正式库存动作一致的已审核生产或质量入库，不能复查收尾", current.receivedQty(),
                current.defectQty(), current.receiptState(), current.orderBlockedReason());
    }

    /**
     * Original requests remain verifiable; the retired manual endpoint cannot create a new closure.
     */
    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public MesExecutionCompletionEntity complete(Long id, Command command) {
        if (command == null) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        ProductionClosureRules.uuid(command.requestId());
        ProductionClosureRules.reason(command.reason());
        var existing = completions.selectByPrimaryKey(command.requestId());
        String hash = ProductionClosureRules.hash(id, command.expectedBasisHash(), command.reason(), ProductionClosureRules.actor());
        if (existing != null && Objects.equals(id, existing.getExecutionId())
                && Objects.equals(existing.getOperatorUserId(), ProductionClosureRules.actor())
                && hash.equals(existing.getRequestHash())) return existing;
        if (existing != null) ParameterChecks.reject(ErrorCode.PRODUCTION_CLOSURE_CONFLICT);
        throw new ProductionClosureBlockedException("手动完工已停用；生产及不良入库审核后按统一规则自动收尾");
    }

    private String blockedReason(MesProductionExecutionEntity execution, ExecutionQuantity projection,
                                 BigDecimal received) {
        if (!List.of(ProductionExecutionStatus.RUNNING.getValue(), ProductionExecutionStatus.FULLY_RECEIVED.getValue())
                .contains(execution.getStatus())) return "执行尚未开工、已取消或状态非法，不能自动完全入库";
        if (!ExecutionPathStatus.LOCKED.getValue().equals(projection.pathStatus())) return "实际生产路径尚未锁定";
        if (projection.nodes().stream().anyMatch(node -> node.historicalConflict()))
            return "存在历史数量超额，须核实后才能完全入库";
        var balanceReason = ProductionCompletionBalance.blockedReason(projection);
        if (balanceReason != null) return balanceReason;
        var unresolved = new WmStockDocumentExample();
        unresolved.createCriteria().andSourceExecutionIdEqualTo(execution.getId()).andDocumentTypeEqualTo(StockDocumentType.MATERIAL_ISSUE.getValue());
        if (hasPendingIssues(issues.selectByExample(unresolved))) return "相关领料仍有未决状态，请核实原请求";
        if (inbound.invalidProductionInboundLines(execution.getId()) > 0) return "正式生产入库明细与库存动作不一致";
        var output = projection.nodes().getLast().effectiveOutputQty();
        if (received.compareTo(output) < 0) return "正常品尚未全部正式入库";
        if (received.compareTo(output) > 0) return "正常品正式入库超过当前有效产出，须核实数量异常";
        return quality.receiptBlockReason(execution.getId());
    }

    private MesExecutionCompletionEntity completion(Long id) {
        var filter = new MesExecutionCompletionExample();
        filter.createCriteria().andExecutionIdEqualTo(id);
        filter.setOrderByClause("recorded_at DESC,id DESC");
        try (var page = PageHelper.offsetPage(0, 1, false)) {
            var rows = completions.selectByExample(filter);
            if (rows.isEmpty()) return null;
            return rows.getFirst();
        }
    }

    static BigDecimal defectQty(ExecutionQuantity projection) {
        return projection.nodes().stream().map(node -> node.netDefectQty()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void append(MesProductionExecutionEntity execution, ExecutionQuantity projection,
                        BigDecimal received, String auditRequestId) {
        // Every new completed input cycle appends a fact; prior cycles remain immutable.
        String requestId = UUID.nameUUIDFromBytes(("fully-received:" + execution.getId() + ":" + auditRequestId)
                .getBytes(StandardCharsets.UTF_8)).toString();
        var row = new MesExecutionCompletionEntity();
        row.setId(requestId);
        row.setExecutionId(execution.getId());
        row.setEffectiveQty(projection.nodes().getLast().effectiveOutputQty());
        row.setReleasedQty(null);
        row.setReceiptQty(BigDecimal.ZERO);
        row.setAcceptedQty(received);
        row.setBasisHash(ProductionClosureRules.hash(projection.routingId(), projection.pathStatus(),
                projection.confirmedInputQty(), projection.nodes().stream()
                        .map(node -> List.of(node.routingNodeId(), ProductionClosureRules.number(node.reportedQty()),
                                ProductionClosureRules.number(node.effectiveOutputQty()),
                                ProductionClosureRules.number(node.netDefectQty()),
                                ProductionClosureRules.number(node.unverifiedDefectQty())))
                        .toList(), defectQty(projection), received, auditRequestId,
                quality.productionBasis(execution.getId()).factIdentities()));
        row.setRequestHash(ProductionClosureRules.hash(requestId, auditRequestId, row.getBasisHash(), ProductionClosureRules.actor()));
        row.setOperatorUserId(ProductionClosureRules.actor());
        row.setRecordedAt(LocalDateTime.now());
        row.setReason("正式入库审核驱动完全入库；审核请求 " + auditRequestId);
        if (completions.insert(row) != 1) ParameterChecks.reject(ErrorCode.PRODUCTION_CLOSURE_CONFLICT);
        execution.setStatus(ProductionExecutionStatus.FULLY_RECEIVED.getValue());
        execution.setEndTime(row.getRecordedAt());
        execution.setUpdateBy(row.getOperatorUserId().toString());
        execution.setUpdateTime(row.getRecordedAt());
        if (executions.updateByPrimaryKey(execution) != 1)
            ParameterChecks.reject(ErrorCode.PRODUCTION_CLOSURE_CONFLICT);
    }

    private boolean hasPendingIssues(List<WmStockDocumentEntity> documents) {
        for (var document : documents) {
            if (StockDocumentStatus.CANCELLED.getValue().equals(document.getStatus())) continue;
            if (!StockDocumentStatus.POSTED.getValue().equals(document.getStatus())
                    && !StockDocumentStatus.DRAFT.getValue().equals(document.getStatus())) return true;
            String reversal = document.getProductionVoidResultStatus();
            if ((document.getProductionVoidOperationId() != null || reversal != null)
                    && (reversal == null || !List.of(MaterialIssueSubmissionStatus.SUCCEEDED.getValue(),
                    MaterialIssueSubmissionStatus.REJECTED.getValue()).contains(reversal))) return true;
            String confirmation = document.getProductionConfirmationStatus();
            if (document.getProductionConfirmationOperationId() != null && confirmation == null) return true;
            if (confirmation != null && !List.of(MaterialIssueSubmissionStatus.SUCCEEDED.getValue(),
                    MaterialIssueSubmissionStatus.REJECTED.getValue()).contains(confirmation)) return true;
            if (StockDocumentStatus.DRAFT.getValue().equals(document.getStatus())
                    && !MaterialIssueSubmissionStatus.REJECTED.getValue().equals(confirmation)) return true;
        }
        return false;
    }
}
