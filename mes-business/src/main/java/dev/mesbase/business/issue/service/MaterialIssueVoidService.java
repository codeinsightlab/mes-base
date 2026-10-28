package dev.mesbase.business.issue.service;

import dev.mesbase.business.issue.dao.MesMaterialIssueMapper;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
import dev.mesbase.business.issue.domain.MaterialIssueStatus;
import dev.mesbase.business.issue.domain.MaterialIssueSubmissionStatus;
import dev.mesbase.business.issue.domain.MesMaterialIssueEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueResource;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.domain.ProductionExecutionStatus;
import dev.mesbase.business.production.service.ProductionExecutionRules;
import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** LOCAL cancellation, input reduction and reversal commit in one business transaction. */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MaterialIssueVoidService {
    private static final String LOCAL_SOURCE = Mode.LOCAL_ONLY.getValue();
    private final MesMaterialIssueMapper issues;
    private final WorkorderMapper orders;
    private final MaterialIssueRules rules;
    private final ProductionExecutionRules executions;
    private final MaterialIssueInputFacts inputFacts;
    private final ProductionQuantityService quantities;

    public MaterialIssueVoidService(MesMaterialIssueMapper issues, WorkorderMapper orders, MaterialIssueRules rules,
                                    ProductionExecutionRules executions, MaterialIssueInputFacts inputFacts,
                                    ProductionQuantityService quantities) {
        this.issues = issues;
        this.orders = orders;
        this.rules = rules;
        this.executions = executions;
        this.inputFacts = inputFacts;
        this.quantities = quantities;
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesMaterialIssueResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public MesMaterialIssueEntity cancel(Long id) {
        return perform(id, false);
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesMaterialIssueResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public MesMaterialIssueEntity reconcile(Long id) {
        return perform(id, true);
    }

    private MesMaterialIssueEntity perform(Long id, boolean recovering) {
        var row = lock(id);
        rules.requireLocalFacts(row);
        boolean pending = Set.of(MaterialIssueStatus.VOID_PENDING.getValue(), MaterialIssueStatus.VOID_UNKNOWN.getValue())
                .contains(row.getStatus());
        if (recovering && !pending) return row;
        if (!recovering && row.getVoidOperationId() != null) return row;
        if (pending) {
            if (row.getVoidOperationId() == null || row.getVoidFromStatus() == null)
                throw new FoundationException(ErrorCode.ISSUE_VOID_CONFLICT);
        } else {
            if (!Set.of(MaterialIssueStatus.DRAFT.getValue(), MaterialIssueStatus.RECORDED.getValue(), MaterialIssueStatus.CONFIRMED.getValue())
                    .contains(row.getStatus())) throw new FoundationException(ErrorCode.ISSUE_VOID_CONFLICT);
            validateExecution(row);
            row.setVoidOperationId(UUID.randomUUID().toString());
            row.setVoidFromStatus(row.getStatus());
            row.setVoidRequestedAt(LocalDateTime.now());
            row.setStatus(MaterialIssueStatus.VOID_PENDING.getValue());
            save(row);
        }
        var input = inputFacts.find(id);
        if (MaterialIssueStatus.CONFIRMED.getValue().equals(row.getVoidFromStatus())
                && (input == null || input.getConfirmedAt() == null || !Boolean.TRUE.equals(input.getStockApplied())))
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        if (input != null) {
            if (!LOCAL_SOURCE.equals(input.getSourceMode())) throw new FoundationException(ErrorCode.BUSINESS_SOURCE_CONFLICT);
            quantities.validateInputReduction(input.getExecutionId());
        }
        // This appends/replays the real reversal with the original cancellation UUID.
        inputFacts.voidConfirmed(row);
        row.setStatus(MaterialIssueStatus.VOIDED.getValue());
        row.setVoidResultStatus(MaterialIssueSubmissionStatus.SUCCEEDED.getValue());
        row.setVoidFinishedAt(LocalDateTime.now());
        save(row);
        return row;
    }

    private void validateExecution(MesMaterialIssueEntity row) {
        if (row.getExecutionId() == null) return;
        var execution = executions.lock(row.getExecutionId());
        if (inputFacts.find(row.getId()) == null && (execution.getStartTime() != null
                || Set.of(ProductionExecutionStatus.RUNNING.getValue(), ProductionExecutionStatus.FULLY_RECEIVED.getValue())
                .contains(execution.getStatus())) || !ProductionExecutionStatus.allowsNormalReporting(execution.getStatus()))
            throw new FoundationException(ErrorCode.EXECUTION_REFERENCED);
    }

    private MesMaterialIssueEntity lock(Long id) {
        rules.lockIssues(List.of(id));
        var row = issues.selectByPrimaryKey(id);
        if (row == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        if (row.getWorkorderId() != null && orders.selectByPrimaryKeyForUpdate(row.getWorkorderId()) == null)
            throw new FoundationException(ErrorCode.NOT_FOUND);
        return row;
    }

    private void save(MesMaterialIssueEntity row) {
        rules.audit(row, false);
        if (issues.updateByPrimaryKey(row) != 1) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
    }
}
