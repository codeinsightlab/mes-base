package dev.mesbase.business.production.service;

import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.wm.dao.WmStockDocumentMapper;
import dev.mesbase.business.wm.domain.WmStockDocumentExample;
import dev.mesbase.business.wm.domain.StockDocumentType;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.error.SafeBusinessMessage;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.infrastructure.persistence.OwnedTransactionManager;
import dev.mesbase.platform.execution.FactoryContext;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import dev.mesbase.business.production.domain.WorkOrderStatus;
import java.util.ArrayList;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Current production commands share WorkOrder -> Execution -> StockDocument lock order.
 */
@Component
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProductionChainLocks {
    private final WorkorderMapper orders;
    private final MesProductionExecutionMapper executions;
    private final WmStockDocumentMapper issues;

    /**
     * Issued only after current locking reads; invalid across threads, transactions, suspension or completion.
     */
    public static final class LockedExecution {
        private final WorkorderEntity order;
        private final MesProductionExecutionEntity execution;
        private final OwnedTransactionManager manager;
        private final Object holder;
        private final Thread thread = Thread.currentThread();
        private final FactoryScope scope = FactoryContext.required();
        private volatile boolean valid = true;

        private LockedExecution(LockedRows rows, OwnedTransactionManager manager, Object holder) {
            this.order = rows.order();
            this.execution = rows.execution();
            this.manager = manager;
            this.holder = holder;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void suspend() {
                    invalidate();
                }

                @Override
                public void afterCompletion(int status) {
                    invalidate();
                }
            });
        }

        public void verify() {
            try {
                if (!valid || thread != Thread.currentThread()
                        || !TransactionSynchronizationManager.isActualTransactionActive()
                        || !scope.equals(FactoryContext.required())
                        || TransactionSynchronizationManager.getResource(manager.getDataSource()) != holder)
                    throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
                // Includes the existing owner, fixed binding and physical pool lease guard.
                manager.verifyAccess();
            } catch (RuntimeException | Error failure) {
                invalidate();
                throw failure;
            }
        }

        public MesProductionExecutionEntity execution() {
            verify();
            return execution;
        }

        public WorkorderEntity order() {
            verify();
            return order;
        }

        public void invalidate() {
            valid = false;
        }
    }

    private record LockedRows(WorkorderEntity order, MesProductionExecutionEntity execution) {
    }

    public ProductionChainLocks(WorkorderMapper orders, MesProductionExecutionMapper executions,
                                WmStockDocumentMapper issues) {
        this.orders = orders;
        this.executions = executions;
        this.issues = issues;
    }

    public static void requireActive(WorkorderEntity row) {
        if (row == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        if (WorkOrderStatus.CANCELLED.getValue().equals(row.getStatus()))
            throw new FoundationException(ErrorCode.WORKORDER_CANCELLED);
        if (!WorkOrderStatus.DRAFT.getValue().equals(row.getStatus())
                && !WorkOrderStatus.RELEASED.getValue().equals(row.getStatus()))
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT,
                    new SafeBusinessMessage("当前工单状态不允许继续生产操作"));
    }

    public void requireActiveExecution(Long id) {
        var row = execution(id);
        requireActive(orders.selectByPrimaryKey(row.getWorkorderId()));
    }

    public void requireActiveExecution(Long id, LockedExecution locked) {
        locked.verify();
        if (!Objects.equals(id, locked.execution().getId()))
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        requireActive(locked.order());
    }

    public WorkorderEntity order(Long id) {
        if (id == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        var row = orders.selectByPrimaryKeyForUpdate(id);
        if (row == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        return row;
    }

    public MesProductionExecutionEntity execution(Long id) {
        return lockRows(id).execution();
    }

    public LockedExecution lockedExecution(Long id, OwnedTransactionManager manager) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive())
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        manager.verifyAccess();
        Object holder = TransactionSynchronizationManager.getResource(manager.getDataSource());
        if (holder == null) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        FactoryContext.required();
        return new LockedExecution(lockRows(id), manager, holder);
    }

    private LockedRows lockRows(Long id) {
        var before = executions.selectByPrimaryKey(id);
        if (before == null) throw new FoundationException(ErrorCode.EXECUTION_NOT_FOUND);
        var order = order(before.getWorkorderId());
        var current = executions.selectByPrimaryKeyForUpdate(id);
        if (current == null) throw new FoundationException(ErrorCode.EXECUTION_NOT_FOUND);
        if (!Objects.equals(before.getWorkorderId(), current.getWorkorderId()))
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        return new LockedRows(order, current);
    }

    public void issues(List<Long> ids) {
        issues(ids, null, null);
    }

    public void issueUpdate(Long id, Long workorderId, Long executionId) {
        issues(List.of(id), workorderId, executionId);
    }

    private void issues(List<Long> ids, Long targetOrder, Long targetExecution) {
        if (ids.isEmpty() || ids.size() > 100) throw new FoundationException(ErrorCode.BAD_REQUEST);
        var f = new WmStockDocumentExample();
        f.createCriteria().andIdIn(ids).andDocumentTypeEqualTo(StockDocumentType.MATERIAL_ISSUE.getValue());
        var before = issues.selectByExample(f);
        if (before.size() != ids.stream().distinct().count()) throw new FoundationException(ErrorCode.NOT_FOUND);
        var orderIds = new ArrayList<Long>();
        var executionIds = new ArrayList<Long>();
        before.forEach(row -> {
            if (row.getSourceWorkorderId() != null) orderIds.add(row.getSourceWorkorderId());
            if (row.getSourceExecutionId() != null) executionIds.add(row.getSourceExecutionId());
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
            var current = issues.selectByPrimaryKeyForUpdate(row.getId());
            if (current == null) throw new FoundationException(ErrorCode.NOT_FOUND);
            if (!Objects.equals(row.getSourceWorkorderId(), current.getSourceWorkorderId()) || !Objects.equals(row.getSourceExecutionId(), current.getSourceExecutionId()))
                throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }
}
