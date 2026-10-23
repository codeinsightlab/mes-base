package dev.mesbase.business.production.service;

import dev.mesbase.business.production.domain.ProductionQuantityInput;

import dev.mesbase.business.basic.dao.WorkstationMapper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.MesProductionReportMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.domain.ExecutionPathStatus;
import dev.mesbase.business.production.domain.MesProductionReportEntity;
import dev.mesbase.business.production.domain.ProductionReportSubmitModels.Command;
import dev.mesbase.business.production.domain.ProductionIdentity;
import dev.mesbase.business.production.domain.ProductionReportModels.ExecutionQuantity;
import dev.mesbase.business.production.service.ExecutionPathService.PathModel;
import java.util.List;
import dev.mesbase.business.production.domain.ProductionReportSubmitModels.Candidate;
import dev.mesbase.business.production.domain.ProductionExecutionStatus;
import dev.mesbase.business.production.domain.ReportRecordType;
import dev.mesbase.business.production.domain.WorkOrderStatus;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import dev.mesbase.infrastructure.persistence.OwnedTransactionManager;
import org.springframework.beans.factory.annotation.Qualifier;

@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProductionReportSubmitService {
    private final BusinessSourceRules sources;

    public record Context(ProductionIdentity identity, ExecutionQuantity quantities, PathModel path,
                          List<Candidate> candidates) {
    }

    private final ProductionIdentityService identities;
    private final ProductionQuantityService quantities;
    private final ExecutionPathService paths;
    private final ReportOperatorEligibilityService eligibility;
    private final ProductionChainLocks locks;
    private final MesProductionReportMapper reports;
    private final MesProductionExecutionMapper executions;
    private final WorkorderMapper orders;
    private final WorkstationMapper workstations;
    private final OwnedTransactionManager transactionManager;

    public ProductionReportSubmitService(ProductionIdentityService identities, ProductionQuantityService quantities,
                                         ExecutionPathService paths, ReportOperatorEligibilityService eligibility,
                                         ProductionChainLocks locks,
                                         MesProductionReportMapper reports, MesProductionExecutionMapper executions,
                                         WorkorderMapper orders,
                                         WorkstationMapper workstations, BusinessSourceRules sources,
                                         @Qualifier("businessTransactionManager") OwnedTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
        this.sources = sources;
        this.identities = identities;
        this.quantities = quantities;
        this.paths = paths;
        this.eligibility = eligibility;
        this.locks = locks;
        this.reports = reports;
        this.executions = executions;
        this.orders = orders;
        this.workstations = workstations;
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true, isolation = Isolation.READ_COMMITTED)
    public Context context(Long executionId) {
        var identity = identities.read(executionId);
        var execution = executions.selectByPrimaryKey(identity.executionId());
        var order = orders.selectByPrimaryKey(identity.workorderId());
        var quantity = quantities.getExecutionQuantities(identity.executionId());
        boolean ready = WorkOrderStatus.RELEASED.getValue()
                .equals(order.getStatus()) && ProductionExecutionStatus.allowsNormalReporting(execution.getStatus())
                && ExecutionPathStatus.LOCKED.getValue().equals(quantity.pathStatus());
        var candidates = new ArrayList<Candidate>();
        Long user = SecurityUtils.getUserId();
        // One batch reads current capability/authorization/assignment for at most 100 actual nodes.
        var failures = ready ? eligibility.ineligibilityReasons(identity.executionId(), quantity.nodes().stream()
                .collect(Collectors.toMap(n -> n.routingNodeId(), n -> n.operationId())), user) : Map.<Long, ErrorCode>of();
        for (var node : quantity.nodes()) {
            var failure = failures.get(node.routingNodeId());
            String qualityBlocked = ProductionQuantityService.positiveReportBlockReason(quantity, node.routingNodeId());
            String status = !ready ? "EXECUTION_NOT_READY" : qualityBlocked != null ? "QUALITY_PENDING" : failure == ErrorCode.OPERATOR_NOT_ASSIGNED ? "NOT_ASSIGNED"
                    : failure != null ? "NO_CAPABILITY" : node.availableQty()
                    .signum() <= 0 ? "NO_QUANTITY" : "AVAILABLE";
            String reason = !ready ? "工单须已下达，执行须未关闭且实际路径已锁定" : qualityBlocked != null ? qualityBlocked : failure != null ? failure.safeMessage()
                    : node.availableQty().signum() <= 0 ? "当前节点没有剩余可报数量" : null;
            candidates.add(new Candidate(node.routingNodeId(), status, reason));
        }
        return new Context(identity, quantity, paths.view(identity.executionId()), candidates);
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public MesProductionReportEntity submit(Command command) {
        validate(command);
        Long user = SecurityUtils.getUserId();
        // A successful replay remains readable even if execution/qualification later changes; it creates no new fact.
        var existing = reports.selectByRequestId(command.requestId());
        if (existing != null) return replay(existing, command, user);
        var locked = locks.lockedExecution(command.executionId(), transactionManager);
        var execution = locked.execution();
        existing = reports.selectByRequestId(command.requestId());
        if (existing != null) return replay(existing, command, user);
        ProductionQuantityInput.check(command.reportQty());
        sources.execution(execution, null);
        if (!ProductionExecutionStatus.allowsNormalReporting(execution.getStatus()))
            throw new FoundationException(ErrorCode.EXECUTION_INVALID);
        var path = quantities.validateReportChange(locked, command.routingNodeId(), command.reportQty());
        eligibility.requireEligible(locked, command.routingNodeId(), user);
        if (command.workstationId() != null && workstations.selectByPrimaryKey(command.workstationId()) == null)
            throw new FoundationException(ErrorCode.REPORT_REFERENCE_INVALID);
        // Reuse the formal start contract, including released order, exact routing, locked path and confirmed issue.
        paths.start(locked, path);
        var now = LocalDateTime.now();
        var row = new MesProductionReportEntity();
        row.setExecutionId(command.executionId());
        row.setRoutingNodeId(command.routingNodeId());
        row.setRequestId(command.requestId());
        row.setRecordType(ReportRecordType.REPORT.getValue());
        row.setQuantityDelta(command.reportQty());
        row.setOperatorUserId(user);
        row.setRecordedBy(user);
        row.setWorkstationId(command.workstationId());
        row.setReportTime(now);
        row.setCreateTime(now);
        row.setReason("正式生产报工");
        try {
            if (reports.insert(row) != 1) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        } catch (DuplicateKeyException collision) {
            // Same-execution retries are serialized above. A cross-execution UUID collision has different payload.
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, collision);
        }
        return reports.selectByPrimaryKey(row.getId());
    }

    private static MesProductionReportEntity replay(MesProductionReportEntity row, Command command, Long user) {
        if (row.getCommandFingerprint() != null || !Objects.equals(row.getExecutionId(), command.executionId()) || !Objects.equals(row.getRoutingNodeId(), command.routingNodeId())
                || !Objects.equals(row.getOperatorUserId(), user) || !Objects.equals(row.getRecordedBy(), user)
                || !Objects.equals(row.getWorkstationId(), command.workstationId()) || row.getQuantityDelta()
                .compareTo(command.reportQty()) != 0)
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        return row;
    }

    private static void validate(Command c) {
        if (c == null || c.executionId() == null || c.routingNodeId() == null || c.reportQty() == null || c.reportQty()
                .signum() <= 0
                || c.reportQty().scale() > 6 || c.reportQty().precision() - c.reportQty()
                .scale() > 14 || c.requestId() == null
                || !c.requestId().matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw new FoundationException(ErrorCode.BAD_REQUEST);
    }
}
